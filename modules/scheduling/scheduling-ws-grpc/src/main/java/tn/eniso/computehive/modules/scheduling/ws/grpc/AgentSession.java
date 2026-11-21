package tn.eniso.computehive.modules.scheduling.ws.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tn.eniso.computehive.contracts.worker.v1.ServerCommand;

/**
 * Stream ouvert par l'agent d'une machine, côté serveur. Un {@link StreamObserver} n'accepte pas
 * d'écritures concurrentes ; or le fil du stream ({@code Registered}) et ceux du module scheduling
 * (placement, renvoi) écrivent en même temps : toutes les écritures sont donc synchronisées.
 */
final class AgentSession {

    private static final Logger log = LoggerFactory.getLogger(AgentSession.class);

    private final String machineId;
    private final StreamObserver<ServerCommand> responses;
    private boolean closed;

    AgentSession(String machineId, StreamObserver<ServerCommand> responses) {
        this.machineId = machineId;
        this.responses = responses;
    }

    String machineId() {
        return machineId;
    }

    /** @return {@code false} si le stream est fermé : l'ordre n'est pas parvenu à l'agent */
    synchronized boolean send(ServerCommand command) {
        if (closed) {
            return false;
        }
        try {
            responses.onNext(command);
            return true;
        } catch (RuntimeException e) {
            closed = true;
            log.warn("Stream of machine {} is broken, command {} not sent: {}", machineId, command.getCommandId(),
                    e.toString());
            return false;
        }
    }

    /** Un nouveau stream de la même machine remplace celui-ci (docs/worker-protocol.md §6.6) : on le ferme. */
    synchronized void closeAsReplaced() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            responses.onError(Status.ABORTED
                    .withDescription("replaced by a newer stream of the same machine")
                    .asRuntimeException());
        } catch (RuntimeException e) {
            log.debug("Replaced stream of machine {} was already closed", machineId);
        }
    }

    /** L'agent a fermé le stream ({@code onCompleted}) : on le ferme de notre côté aussi. */
    synchronized void complete() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            responses.onCompleted();
        } catch (RuntimeException e) {
            log.debug("Stream of machine {} was already closed", machineId);
        }
    }

    /** Le stream est rompu ({@code onError}) : plus rien ne doit y être écrit. */
    synchronized void markBroken() {
        closed = true;
    }
}
