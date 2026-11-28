package tn.eniso.computehive.simulator;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import tn.eniso.computehive.contracts.worker.v1.Ack;
import tn.eniso.computehive.contracts.worker.v1.AgentMessage;
import tn.eniso.computehive.contracts.worker.v1.Registered;
import tn.eniso.computehive.contracts.worker.v1.ServerCommand;
import tn.eniso.computehive.contracts.worker.v1.WorkerGatewayGrpc;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Faux serveur pour tester le faux agent : répond {@code Registered} (heartbeat toutes les secondes, bail de 3 s),
 * acquitte comme le vrai serveur les heartbeats et les messages durables ({@code Ack}), garde les messages reçus,
 * et permet d'envoyer des ordres, de couper le stream ou de se taire.
 */
final class FakeGateway extends WorkerGatewayGrpc.WorkerGatewayImplBase {

    static final int LEASE_TTL_SECONDS = 3;

    private final BlockingQueue<AgentMessage> received = new LinkedBlockingQueue<>();
    private final BlockingQueue<Throwable> streamErrors = new LinkedBlockingQueue<>();
    private final AtomicInteger streams = new AtomicInteger();
    private StreamObserver<ServerCommand> current;
    private volatile boolean acknowledging = true;
    private volatile boolean silent;

    @Override
    public StreamObserver<AgentMessage> connect(StreamObserver<ServerCommand> responses) {
        streams.incrementAndGet();
        synchronized (this) {
            current = responses;
        }
        return new StreamObserver<>() {
            @Override
            public void onNext(AgentMessage message) {
                received.add(message);
                if (message.hasRegister()) {
                    send(ServerCommand.newBuilder().setCommandId("cmd-registered")
                            .setRegistered(Registered.newBuilder().setMachineId("machine-" + message.getRegister()
                                    .getAgentId()).setHeartbeatIntervalSeconds(1)
                                    .setLeaseTtlSeconds(LEASE_TTL_SECONDS))
                            .build());
                } else if (acknowledging && !message.hasProgress()) {
                    send(ServerCommand.newBuilder()
                            .setAck(Ack.newBuilder().setMessageId(message.getMessageId())).build());
                }
            }

            @Override
            public void onError(Throwable error) {
                streamErrors.add(error);
            }

            @Override
            public void onCompleted() {
                responses.onCompleted();
            }
        };
    }

    synchronized void send(ServerCommand command) {
        if (!silent) {
            current.onNext(command);
        }
    }

    /** N'acquitte plus aucun message : l'agent doit les garder et les renvoyer. */
    void stopAcknowledging() {
        acknowledging = false;
    }

    /** Le serveur se tait (stream ouvert, plus rien n'en sort), comme un serveur bloqué ou un réseau coupé. */
    void goSilent() {
        silent = true;
    }

    /** Coupe le stream côté serveur, comme lors d'un redémarrage du serveur. */
    synchronized void dropStream() {
        current.onError(Status.UNAVAILABLE.withDescription("server restarting").asRuntimeException());
    }

    /** Prochain message reçu, heartbeats ignorés. */
    AgentMessage next() throws InterruptedException {
        while (true) {
            AgentMessage message = received.poll(5, TimeUnit.SECONDS);
            if (message == null) {
                throw new AssertionError("no message received within 5 s");
            }
            if (!message.hasHeartbeat()) {
                return message;
            }
        }
    }

    /** Prochain message reçu qui vérifie {@code type}, les autres étant ignorés. */
    AgentMessage next(Predicate<AgentMessage> type) throws InterruptedException {
        while (true) {
            AgentMessage message = next();
            if (type.test(message)) {
                return message;
            }
        }
    }

    /** Prochain heartbeat reçu. */
    AgentMessage nextHeartbeat() throws InterruptedException {
        while (true) {
            AgentMessage message = received.poll(5, TimeUnit.SECONDS);
            if (message == null) {
                throw new AssertionError("no heartbeat received within 5 s");
            }
            if (message.hasHeartbeat()) {
                return message;
            }
        }
    }

    /** {@code true} si aucun message autre qu'un heartbeat n'arrive pendant {@code millis}. */
    boolean quietFor(long millis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            AgentMessage message = received.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (message != null && !message.hasHeartbeat()) {
                return false;
            }
        }
        return true;
    }

    Throwable nextStreamError() throws InterruptedException {
        return streamErrors.poll(5, TimeUnit.SECONDS);
    }

    int streamCount() {
        return streams.get();
    }
}
