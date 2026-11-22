package tn.eniso.computehive.modules.scheduling.ws.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.contracts.worker.v1.AgentMessage;
import tn.eniso.computehive.contracts.worker.v1.ServerCommand;
import tn.eniso.computehive.contracts.worker.v1.WorkerGatewayGrpc;
import tn.eniso.computehive.core.infra.exception.ComputeHiveException;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.exception.RemoteModuleUnavailableException;
import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.core.infra.log.LogKeys;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentHello;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentRegistered;
import tn.eniso.computehive.modules.scheduling.service.api.SchedulingModule;

/**
 * Porte d'entrée des agents : {@code WorkerGateway.Connect}. L'agent ouvre lui-même le stream
 * (aucun port à ouvrir sur les PC) ; le serveur répond sur le même stream.
 *
 * <p>Aucune logique métier : chaque message reçu est traduit ({@link ProtoTranslator}) en <b>un</b> appel
 * de la façade {@link SchedulingModule}, qui décide de tout (fencing, dédoublonnage, appels à jobs et machines).</p>
 *
 * <ul>
 *   <li>Le premier message d'un stream doit être {@code Register} ; sinon le stream est refusé.</li>
 *   <li>Un message refusé par le module (donnée invalide, tentative inconnue…) est journalisé ; le stream
 *       reste ouvert pour les suivants.</li>
 *   <li>Un heartbeat d'une machine inconnue ferme le stream ({@code NOT_FOUND}) : l'agent se reconnecte et se
 *       réinscrit.</li>
 * </ul>
 */
@Component
public class WorkerGatewayService extends WorkerGatewayGrpc.WorkerGatewayImplBase {

    private static final Logger log = LoggerFactory.getLogger(WorkerGatewayService.class);

    private final SchedulingModule scheduling;
    private final AgentSessionRegistry sessions;

    public WorkerGatewayService(SchedulingModule scheduling, AgentSessionRegistry sessions) {
        this.scheduling = scheduling;
        this.sessions = sessions;
    }

    @Override
    public StreamObserver<AgentMessage> connect(StreamObserver<ServerCommand> responses) {
        return new AgentStream(responses);
    }

    /** Un stream ouvert par un agent : il ne connaît sa machine qu'après le {@code Register}. */
    private final class AgentStream implements StreamObserver<AgentMessage> {

        private final StreamObserver<ServerCommand> responses;
        private AgentSession session;
        private boolean refused;

        private AgentStream(StreamObserver<ServerCommand> responses) {
            this.responses = responses;
        }

        @Override
        public void onNext(AgentMessage message) {
            if (refused) {
                return;
            }
            if (!message.getMessageId().isEmpty()) {
                MDC.put(LogKeys.CORRELATION_ID, message.getMessageId());
            }
            if (session != null) {
                MDC.put(LogKeys.WORKER_ID, session.machineId());
            }
            try {
                if (session == null) {
                    register(message);
                } else {
                    dispatch(message);
                }
            } finally {
                MDC.remove(LogKeys.CORRELATION_ID);
                MDC.remove(LogKeys.WORKER_ID);
            }
        }

        @Override
        public void onError(Throwable error) {
            if (session != null) {
                session.markBroken();
                sessions.close(session);
                log.info("Stream of machine {} broken: {}", session.machineId(), Status.fromThrowable(error));
            }
        }

        @Override
        public void onCompleted() {
            if (session != null) {
                sessions.close(session);
                session.complete();
                log.info("Machine {} closed its stream", session.machineId());
            } else if (!refused) {
                responses.onCompleted();
            }
        }

        /**
         * Inscription, puis ouverture de la session, réponse {@code Registered}, et enfin resynchronisation :
         * dans cet ordre, les {@code StopJob} de la resynchronisation trouvent la session ouverte.
         */
        private void register(AgentMessage message) {
            if (!message.hasRegister()) {
                refuse(Status.FAILED_PRECONDITION.withDescription("the first message of a stream must be Register"));
                return;
            }
            AgentHello hello;
            AgentRegistered registered;
            try {
                hello = ProtoTranslator.toHello(message.getRegister());
                registered = scheduling.registerAgent(hello);
            } catch (ValidationException e) {
                refuse(Status.INVALID_ARGUMENT.withDescription(e.getMessage()));
                return;
            } catch (RemoteModuleUnavailableException e) {
                // M/P : machines est injoignable. UNAVAILABLE dit à l'agent de se reconnecter plus tard.
                log.warn("Registration of agent {} postponed: {}", message.getRegister().getAgentId(), e.getMessage());
                refuse(Status.UNAVAILABLE.withDescription("server temporarily unavailable, retry later"));
                return;
            }
            session = new AgentSession(registered.machineId(), responses);
            sessions.open(session);
            session.send(ProtoTranslator.toRegistered(registered));
            MDC.put(LogKeys.WORKER_ID, registered.machineId());
            log.info("Agent {} connected as machine {}", hello.agentId(), registered.machineId());
            safely(message, () -> scheduling.resynchronize(registered.machineId(), hello.running()));
        }

        private void dispatch(AgentMessage message) {
            String machineId = session.machineId();
            String messageId = message.getMessageId();
            switch (message.getPayloadCase()) {
                case HEARTBEAT -> {
                    try {
                        scheduling.heartbeat(machineId, ProtoTranslator.toHeartbeat(message.getHeartbeat()));
                    } catch (NotFoundException e) {
                        closeUnknownMachine();
                    } catch (ComputeHiveException e) {
                        log.warn("Heartbeat of machine {} rejected: {}", machineId, e.getMessage());
                    }
                }
                case PROGRESS -> safely(message, () -> scheduling.reportProgress(machineId, messageId,
                        ProtoTranslator.toProgress(message.getProgress())));
                case CHECKPOINT_SAVED -> safely(message, () -> scheduling.reportCheckpoint(machineId, messageId,
                        ProtoTranslator.toCheckpoint(message.getCheckpointSaved())));
                case FINISHED -> safely(message, () -> scheduling.reportFinished(machineId, messageId,
                        ProtoTranslator.toFinished(message.getFinished())));
                case COMMAND_ACK -> safely(message, () -> scheduling.acknowledge(machineId, messageId,
                        ProtoTranslator.toAck(message.getCommandAck())));
                case REGISTER -> log.warn("Machine {} sent Register again on the same stream: ignored", machineId);
                case PAYLOAD_NOT_SET -> log.warn("Empty message {} from machine {} ignored", messageId, machineId);
            }
        }

        /** Un message refusé ou en échec est journalisé ; le stream reste ouvert pour les messages suivants. */
        private void safely(AgentMessage message, Runnable call) {
            try {
                call.run();
            } catch (ComputeHiveException e) {
                log.warn("{} message {} rejected: {}", message.getPayloadCase(), message.getMessageId(), e.getMessage());
            } catch (RuntimeException e) {
                log.error("{} message {} failed; the agent may send it again", message.getPayloadCase(),
                        message.getMessageId(), e);
            }
        }

        private void closeUnknownMachine() {
            log.warn("Heartbeat from unknown machine {}: stream closed, the agent must register again",
                    session.machineId());
            sessions.close(session);
            session.markBroken();
            responses.onError(Status.NOT_FOUND
                    .withDescription("unknown machine " + session.machineId() + ": register again")
                    .asRuntimeException());
            refused = true;
        }

        private void refuse(Status status) {
            refused = true;
            log.warn("Agent stream refused: {}", status.getDescription());
            responses.onError(status.asRuntimeException());
        }
    }
}
