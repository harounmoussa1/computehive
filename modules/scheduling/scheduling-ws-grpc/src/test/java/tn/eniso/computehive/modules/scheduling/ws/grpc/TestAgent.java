package tn.eniso.computehive.modules.scheduling.ws.grpc;

import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import tn.eniso.computehive.contracts.worker.v1.AgentMessage;
import tn.eniso.computehive.contracts.worker.v1.Capabilities;
import tn.eniso.computehive.contracts.worker.v1.GpuInfo;
import tn.eniso.computehive.contracts.worker.v1.Register;
import tn.eniso.computehive.contracts.worker.v1.RunningJob;
import tn.eniso.computehive.contracts.worker.v1.ServerCommand;
import tn.eniso.computehive.contracts.worker.v1.WorkerGatewayGrpc;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Agent minimal pour les tests : ouvre le stream, envoie des messages et garde les ordres reçus. */
final class TestAgent {

    private final BlockingQueue<ServerCommand> received = new LinkedBlockingQueue<>();
    private final CompletableFuture<Status> closed = new CompletableFuture<>();
    private final AtomicInteger messages = new AtomicInteger();
    private final StreamObserver<AgentMessage> requests;

    TestAgent(Channel channel) {
        requests = WorkerGatewayGrpc.newStub(channel).connect(new StreamObserver<>() {
            @Override
            public void onNext(ServerCommand command) {
                received.add(command);
            }

            @Override
            public void onError(Throwable error) {
                closed.complete(Status.fromThrowable(error));
            }

            @Override
            public void onCompleted() {
                closed.complete(Status.OK);
            }
        });
    }

    static Register register(String agentId, RunningJob... running) {
        return Register.newBuilder()
                .setAgentId(agentId)
                .setName("pc-" + agentId)
                .setAgentVersion("0.1.0")
                .setOs("linux")
                .setCpuCores(8)
                .setRamTotalMb(32_000)
                .addGpus(GpuInfo.newBuilder().setIndex(0).setName("RTX 3060").setMemoryTotalMb(12_288))
                .setCapabilities(Capabilities.newBuilder().setCuda(true))
                .addAllRunning(java.util.List.of(running))
                .build();
    }

    /** Envoie un message avec un nouveau {@code message_id} ; renvoie ce {@code message_id}. */
    String send(AgentMessage.Builder message) {
        String messageId = "msg-" + messages.incrementAndGet();
        requests.onNext(message.setMessageId(messageId).build());
        return messageId;
    }

    void close() {
        requests.onCompleted();
    }

    ServerCommand next() throws InterruptedException {
        ServerCommand command = received.poll(5, TimeUnit.SECONDS);
        if (command == null) {
            throw new AssertionError("no command received within 5 s");
        }
        return command;
    }

    boolean hasNoPendingCommand() {
        return received.isEmpty();
    }

    Status closedStatus() throws Exception {
        return closed.get(5, TimeUnit.SECONDS);
    }

    boolean isOpen() {
        return !closed.isDone();
    }
}
