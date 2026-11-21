package tn.eniso.computehive.simulator;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import tn.eniso.computehive.contracts.worker.v1.AgentMessage;
import tn.eniso.computehive.contracts.worker.v1.Registered;
import tn.eniso.computehive.contracts.worker.v1.ServerCommand;
import tn.eniso.computehive.contracts.worker.v1.WorkerGatewayGrpc;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Faux serveur pour tester le faux agent : répond {@code Registered} (heartbeat toutes les secondes),
 * garde les messages reçus, et permet d'envoyer des ordres ou de couper le stream.
 */
final class FakeGateway extends WorkerGatewayGrpc.WorkerGatewayImplBase {

    private final BlockingQueue<AgentMessage> received = new LinkedBlockingQueue<>();
    private final BlockingQueue<Throwable> streamErrors = new LinkedBlockingQueue<>();
    private final AtomicInteger streams = new AtomicInteger();
    private StreamObserver<ServerCommand> current;

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
                                    .getAgentId()).setHeartbeatIntervalSeconds(1).setLeaseTtlSeconds(3))
                            .build());
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
        current.onNext(command);
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
