package tn.eniso.computehive.simulator;

import com.sun.net.httpserver.HttpServer;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.eniso.computehive.contracts.worker.v1.AgentMessage;
import tn.eniso.computehive.contracts.worker.v1.JobParams;
import tn.eniso.computehive.contracts.worker.v1.LaunchJob;
import tn.eniso.computehive.contracts.worker.v1.ServerCommand;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le faux agent et le stockage des checkpoints : il dépose le fichier avant d'annoncer {@code CheckpointSaved},
 * et télécharge puis vérifie le checkpoint de reprise avant de démarrer. Le serveur de stockage est un serveur
 * HTTP du JDK qui imite l'API {@code /api/checkpoints/...}.
 */
class SimulatedAgentCheckpointTest {

    private static final Behaviour FAST = Behaviour.normal().withEpochDuration(Duration.ofMillis(20))
            .withMaxReconnectDelay(Duration.ofSeconds(1));

    private final FakeGateway gateway = new FakeGateway();
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final Map<String, String> announcedSha = new ConcurrentHashMap<>();
    private volatile boolean refuseUploads;
    private volatile String lastAuthorization;
    private HttpServer storage;
    private Server server;
    private ManagedChannel channel;
    private SimulatedAgent agent;

    @BeforeEach
    void setUp() throws Exception {
        storage = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        storage.createContext("/api/checkpoints/", exchange -> {
            String key = exchange.getRequestURI().getPath().substring("/api/checkpoints/".length());
            lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            if ("PUT".equals(exchange.getRequestMethod())) {
                byte[] body = exchange.getRequestBody().readAllBytes();
                if (refuseUploads) {
                    respond(exchange, 503, new byte[0]);
                    return;
                }
                files.put(key, body);
                announcedSha.put(key, exchange.getRequestHeaders().getFirst(CheckpointClient.SHA256_HEADER));
                respond(exchange, 201, new byte[0]);
            } else if (files.containsKey(key)) {
                respond(exchange, 200, files.get(key));
            } else {
                respond(exchange, 404, new byte[0]);
            }
        });
        storage.start();
        String name = UUID.randomUUID().toString();
        server = InProcessServerBuilder.forName(name).directExecutor().addService(gateway).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        agent = new SimulatedAgent(channel, AgentProfile.gpu("a1", "RTX 3060", 12_288), FAST,
                new CheckpointClient("http://localhost:" + storage.getAddress().getPort(), "labo-s3cr3t"));
        agent.start();
        gateway.next();
    }

    @AfterEach
    void tearDown() {
        agent.close();
        channel.shutdownNow();
        server.shutdownNow();
        storage.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static ServerCommand launch(int epochs, int resumeFromEpoch, String resumeKey, String resumeSha) {
        return ServerCommand.newBuilder().setCommandId("cmd-L").setLaunch(LaunchJob.newBuilder()
                .setJobId("j-7").setAttemptId(2).setJobType("MNIST_TRAIN")
                .setParams(JobParams.newBuilder().setEpochs(epochs).setLearningRate(0.01).setBatchSize(64))
                .setResumeFromEpoch(resumeFromEpoch).setResumeCheckpointKey(resumeKey)
                .setResumeCheckpointSha256(resumeSha)).build();
    }

    private AgentMessage nextMatching(java.util.function.Predicate<AgentMessage> condition) throws InterruptedException {
        AgentMessage message;
        do {
            message = gateway.next();
        } while (!condition.test(message));
        return message;
    }

    @Test
    void theFileIsUploadedBeforeTheCheckpointIsAnnounced() throws Exception {
        gateway.send(launch(2, 0, "", ""));

        AgentMessage saved = nextMatching(AgentMessage::hasCheckpointSaved);

        String key = saved.getCheckpointSaved().getStorageKey();
        assertThat(key).isEqualTo("j-7/attempts/2/epochs/2");
        assertThat(files).containsKey(key);
        assertThat(saved.getCheckpointSaved().getSha256())
                .isEqualTo(CheckpointClient.sha256(files.get(key)))
                .isEqualTo(announcedSha.get(key));
        assertThat(saved.getCheckpointSaved().getSizeBytes()).isEqualTo(files.get(key).length);
        assertThat(lastAuthorization).as("shared agent token sent").isEqualTo("Bearer labo-s3cr3t");
    }

    @Test
    void aRefusedUploadIsNotAnnounced() throws Exception {
        refuseUploads = true;
        gateway.send(launch(2, 0, "", ""));

        AgentMessage finished = nextMatching(m -> m.hasFinished() || m.hasCheckpointSaved());

        assertThat(finished.hasFinished()).as("no CheckpointSaved before the end").isTrue();
        assertThat(finished.getFinished().getLastCheckpointEpoch()).isZero();
    }

    @Test
    void aResumedJobDownloadsAndVerifiesItsCheckpointFirst() throws Exception {
        byte[] previous = "weights of attempt 1, epoch 2".getBytes(StandardCharsets.UTF_8);
        files.put("j-7/attempts/1/epochs/2", previous);

        gateway.send(launch(4, 2, "j-7/attempts/1/epochs/2", CheckpointClient.sha256(previous)));

        assertThat(gateway.next().getCommandAck().getAccepted()).isTrue();
        assertThat(nextMatching(AgentMessage::hasProgress).getProgress().getEpoch()).isEqualTo(3);
    }

    @Test
    void aCorruptedOrMissingCheckpointMakesTheAgentRefuseTheJob() throws Exception {
        files.put("j-7/attempts/1/epochs/2", "tampered".getBytes(StandardCharsets.UTF_8));

        gateway.send(launch(4, 2, "j-7/attempts/1/epochs/2", "0".repeat(64)));

        AgentMessage ack = gateway.next();
        assertThat(ack.getCommandAck().getAccepted()).isFalse();
        assertThat(ack.getCommandAck().getReason()).contains("checkpoint unavailable", "corrupted");
    }
}
