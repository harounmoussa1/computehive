package tn.eniso.computehive.app.modulith;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tn.eniso.computehive.contracts.worker.v1.AgentMessage;
import tn.eniso.computehive.contracts.worker.v1.Register;
import tn.eniso.computehive.contracts.worker.v1.ServerCommand;
import tn.eniso.computehive.contracts.worker.v1.WorkerGatewayGrpc;
import tn.eniso.computehive.core.infra.util.Sha256;
import tn.eniso.computehive.modules.dashboard.infra.JobDetails;
import tn.eniso.computehive.modules.files.infra.FileInfo;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;
import tn.eniso.computehive.modules.machines.service.api.MachinesModule;
import tn.eniso.computehive.modules.quotas.infra.UsageInfo;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentInfo;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentStatus;
import tn.eniso.computehive.modules.scheduling.service.api.SchedulingModule;
import tn.eniso.computehive.modules.scheduling.ws.grpc.WorkerGatewayServer;
import tn.eniso.computehive.simulator.AgentProfile;
import tn.eniso.computehive.simulator.Behaviour;
import tn.eniso.computehive.simulator.CheckpointClient;
import tn.eniso.computehive.simulator.SimulatedAgent;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jalon de l'étape 7 : la vraie application M/1, sur un vrai PostgreSQL, avec de faux agents qui parlent gRPC.
 *
 * <p>Un job est soumis par l'API REST ; le PC qui l'exécute tombe en panne en cours de route ; le job doit
 * reprendre sur un autre PC depuis son dernier checkpoint, se terminer, être compté par quotas et notifié à son
 * propriétaire. Les durées du protocole sont raccourcies (heartbeat 1 s, bail 3 s) pour que le test reste rapide.
 * Ignoré si Docker n'est pas disponible sur la machine.</p>
 */
// L'ordre compte : JUnit referme les extensions dans l'ordre inverse de leur déclaration. Le contexte Spring
// (@DirtiesContext) est ainsi fermé avant l'arrêt du conteneur PostgreSQL, et ses tâches périodiques ne
// tournent jamais contre une base arrêtée.
@Testcontainers(disabledWithoutDocker = true)
// Spring Boot désactive l'export des métriques dans les tests : on le réactive, le test lit /actuator/prometheus.
@AutoConfigureObservability(tracing = false)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "computehive.grpc.port=0",
        "computehive.agents.token=" + ModulithEndToEndIT.AGENT_TOKEN,
        "computehive.machines.heartbeat-interval=1s",
        "computehive.machines.lease-ttl=3s",
        "computehive.machines.detection-interval=500ms",
        "computehive.machines.startup-grace=0s",
        "computehive.scheduling.placement-interval=200ms",
        "computehive.scheduling.reconciliation-interval=1s",
        "computehive.scheduling.lost-job-grace=3s"
})
@DirtiesContext
class ModulithEndToEndIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final String AGENT_TOKEN = "e2e-labo-token";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final Behaviour FAST = Behaviour.normal()
            .withEpochDuration(Duration.ofMillis(300))
            .withMaxReconnectDelay(Duration.ofSeconds(1));

    @TempDir
    static Path checkpointDirectory;

    @DynamicPropertySource
    static void checkpointStorage(DynamicPropertyRegistry registry) {
        registry.add("computehive.checkpoints.local.directory", () -> checkpointDirectory.toString());
        registry.add("computehive.files.local.directory", () -> checkpointDirectory.resolve("files").toString());
    }

    @LocalServerPort
    private int httpPort;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private WorkerGatewayServer gateway;

    @Autowired
    private SchedulingModule scheduling;

    @Autowired
    private MachinesModule machines;

    private final List<SimulatedAgent> agents = new ArrayList<>();
    private final ListAppender<ILoggingEvent> notifications = new ListAppender<>();
    private final Logger notificationLogger = (Logger) LoggerFactory.getLogger("computehive.notifications");
    private ManagedChannel channel;

    @BeforeEach
    void setUp() {
        Metadata credentials = new Metadata();
        credentials.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + AGENT_TOKEN);
        channel = NettyChannelBuilder.forAddress("localhost", gateway.port()).usePlaintext()
                .intercept(MetadataUtils.newAttachHeadersInterceptor(credentials)).build();
        notificationLogger.setLevel(Level.INFO);
        notifications.start();
        notificationLogger.addAppender(notifications);
    }

    @AfterEach
    void tearDown() {
        agents.forEach(SimulatedAgent::close);
        channel.shutdownNow();
        notificationLogger.detachAppender(notifications);
    }

    private void startAgent(String agentId, Behaviour behaviour) {
        SimulatedAgent agent = new SimulatedAgent(channel, AgentProfile.gpu(agentId, "RTX 3060", 12_288), behaviour,
                new CheckpointClient("http://localhost:" + httpPort, AGENT_TOKEN));
        agents.add(agent);
        agent.start();
    }

    private JobInfo submitJob(int epochs) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", "u-1");
        headers.set("X-User-Name", "Haroun");
        String body = """
                {"name": "mnist-e2e", "type": "MNIST_TRAIN",
                 "params": {"epochs": %d, "learningRate": 0.01, "batchSize": 64},
                 "requirements": {"gpu": true, "minGpuMemoryMb": 4096}}
                """.formatted(epochs);
        ResponseEntity<JobInfo> response = rest.postForEntity("/api/jobs", new HttpEntity<>(body, headers), JobInfo.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private JobInfo job(String id) {
        return rest.getForObject("/api/jobs/" + id, JobInfo.class);
    }

    /** Attend que le job, relu par l'API REST, vérifie {@code condition}. */
    private JobInfo awaitJob(String id, Predicate<JobInfo> condition, String description) throws InterruptedException {
        return await(() -> job(id), condition, "job " + description);
    }

    /**
     * Relit {@code state} jusqu'à ce qu'il vérifie {@code condition}. Les modules écrivent chacun dans leur propre
     * transaction (jamais de transaction distribuée) : à la fin d'un job, le job, sa tentative, la consommation et
     * la notification sont mis à jour l'un après l'autre. Le test attend donc chaque effet, au lieu de supposer qu'ils
     * sont tous visibles au même instant.
     */
    private static <T> T await(Supplier<T> state, Predicate<T> condition, String description)
            throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        T value = state.get();
        while (!condition.test(value)) {
            assertThat(System.nanoTime()).as("%s within %s, last state: %s", description, TIMEOUT, value)
                    .isLessThan(deadline);
            Thread.sleep(100);
            value = state.get();
        }
        return value;
    }

    @Test
    void aJobSurvivesTheCrashOfItsMachineAndResumesElsewhereFromItsCheckpoint() throws Exception {
        startAgent("pc-a", FAST.crashingAt(4));
        JobInfo submitted = submitJob(8);

        JobInfo onFirstMachine = awaitJob(submitted.id(), j -> j.status() == JobStatus.RUNNING, "running on pc-a");
        assertThat(onFirstMachine.machine().name()).isEqualTo("sim-pc-a");
        startAgent("pc-b", FAST);

        JobInfo done = awaitJob(submitted.id(), j -> j.status() == JobStatus.DONE, "done after the crash");

        // Le job a fini sur le second PC, depuis le checkpoint écrit par le premier avant sa panne.
        assertThat(done.machine().name()).isEqualTo("sim-pc-b");
        assertThat(done.currentEpoch()).isEqualTo(8);
        assertThat(done.accuracy()).isBetween(0.8, 1.0);
        List<AssignmentInfo> attempts = await(() -> scheduling.attemptsOf(submitted.id()),
                all -> all.size() == 2 && all.get(1).status() == AssignmentStatus.COMPLETED, "second attempt completed");
        assertThat(attempts).extracting(AssignmentInfo::status)
                .containsExactly(AssignmentStatus.LOST, AssignmentStatus.COMPLETED);
        assertThat(attempts.get(1).resumeFrom().epoch()).as("resumed from the checkpoint of epoch 2").isEqualTo(2);
        assertThat(machines.findById(attempts.get(0).machine().id()).orElseThrow().status())
                .isEqualTo(MachineStatus.DEAD);
        JobDetails onDashboard = rest.getForObject("/api/dashboard/jobs/" + submitted.id(), JobDetails.class);
        assertThat(onDashboard.attempts()).as("the dashboard shows both attempts").extracting(AssignmentInfo::status)
                .containsExactly(AssignmentStatus.LOST, AssignmentStatus.COMPLETED);

        // Le checkpoint de reprise a réellement été déposé par pc-a, vérifié, puis téléchargé par pc-b.
        String resumeKey = attempts.get(1).resumeFrom().checkpointKey();
        assertThat(resumeKey).isEqualTo(submitted.id() + "/attempts/1/epochs/2");
        assertThat(checkpointDirectory.resolve(resumeKey)).isRegularFile();
        HttpHeaders agentHeaders = new HttpHeaders();
        agentHeaders.setBearerAuth(AGENT_TOKEN);
        ResponseEntity<byte[]> file = rest.exchange("/api/checkpoints/" + resumeKey, HttpMethod.GET,
                new HttpEntity<>(agentHeaders), byte[].class);
        assertThat(file.getHeaders().getFirst("X-Checkpoint-Sha256"))
                .isEqualTo(attempts.get(1).resumeFrom().checkpointSha256());
        assertThat(done.lastCheckpoint().storageKey()).as("pc-b's own checkpoints").contains("/attempts/2/");

        // Le temps de calcul est compté pour le propriétaire, qui est prévenu.
        UsageInfo usage = await(() -> rest.getForObject("/api/quotas/u-1", UsageInfo.class),
                u -> u.jobsCompleted() == 1, "usage recorded");
        assertThat(usage.jobsCompleted()).isEqualTo(1);
        assertThat(usage.gpuSeconds()).isPositive();
        assertThat(usage.priority()).isLessThan(1.0);
        await(() -> List.copyOf(notifications.list), events -> events.stream().map(ILoggingEvent::getFormattedMessage)
                .anyMatch(m -> m.contains("[job.done]") && m.contains("u-1") && m.contains("mnist-e2e")), "owner notified");

        // Prometheus voit la panne et la reprise (ADR 0014). Le contexte est partagé par les tests : au moins 1.
        await(() -> rest.getForObject("/actuator/prometheus", String.class), metrics ->
                metric(metrics, "computehive_machines_failures_total") >= 1
                        && metric(metrics, "computehive_attempts_ended_total", "outcome=\"lost\"") >= 1
                        && metric(metrics, "computehive_attempts_started_total", "start=\"checkpoint\"") >= 1
                        && metric(metrics, "computehive_jobs_reassigned_total") >= 1
                        && metric(metrics, "computehive_jobs_finished_total", "status=\"done\"") >= 1
                        && metric(metrics, "computehive_agent_messages_total",
                        "type=\"checkpoint_saved\"", "outcome=\"accepted\"") >= 1
                        && metric(metrics, "computehive_machines", "status=\"dead\"") >= 1,
                "crash and resume visible on /actuator/prometheus");
    }

    /**
     * Valeur d'une série de l'exposition Prometheus (somme si plusieurs séries correspondent), {@code 0} si absente.
     *
     * @param labels étiquettes que la série doit porter, ex. {@code status="done"}
     */
    private static double metric(String exposition, String name, String... labels) {
        double sum = 0;
        for (String line : exposition.split("\n")) {
            boolean sameName = line.startsWith(name + "{") || line.startsWith(name + " ");
            if (!sameName || !List.of(labels).stream().allMatch(line::contains)) {
                continue;
            }
            sum += Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1));
        }
        return sum;
    }

    @Test
    void aProgramWithoutTheAgentTokenCanNeitherConnectNorTouchCheckpoints() throws Exception {
        ManagedChannel intruder = NettyChannelBuilder.forAddress("localhost", gateway.port()).usePlaintext().build();
        try {
            BlockingQueue<Status> closed = new LinkedBlockingQueue<>();
            StreamObserver<AgentMessage> stream = WorkerGatewayGrpc.newStub(intruder).connect(new StreamObserver<>() {
                @Override
                public void onNext(ServerCommand command) {
                }

                @Override
                public void onError(Throwable error) {
                    closed.add(Status.fromThrowable(error));
                }

                @Override
                public void onCompleted() {
                    closed.add(Status.OK);
                }
            });
            stream.onNext(AgentMessage.newBuilder().setMessageId("m-1").setRegister(Register.newBuilder()
                    .setAgentId("intruder").setName("intruder")).build());

            assertThat(closed.poll(10, TimeUnit.SECONDS)).extracting(Status::getCode)
                    .isEqualTo(Status.Code.UNAUTHENTICATED);
            assertThat(machines.findAll()).noneMatch(machine -> machine.name().equals("intruder"));
        } finally {
            intruder.shutdownNow();
        }

        // Client HTTP du JDK, comme un agent : celui de TestRestTemplate ne sait pas lire un 401 sur un PUT.
        HttpResponse<String> upload = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + httpPort + "/api/checkpoints/j-1/attempts/1/epochs/1"))
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[]{1, 2, 3}))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(upload.statusCode()).isEqualTo(401);
        assertThat(upload.body()).contains("missing or invalid agent token");
    }

    @Test
    void aDatasetUploadedToTheLibraryIsDownloadedByAnAgentWithResume() throws Exception {
        byte[] zip = zipOf("labels.csv", "0,1,2,3,4,5,6,7,8,9\n");
        HttpHeaders user = new HttpHeaders();
        user.set("X-User-Id", "u-9");
        user.setContentType(MediaType.parseMediaType("application/zip"));

        // Dépôt depuis le tableau de bord : vérifié, rangé sous son empreinte, visible dans la bibliothèque.
        ResponseEntity<FileInfo> uploaded = rest.exchange("/api/library/datasets/mnist", HttpMethod.POST,
                new HttpEntity<>(zip, user), FileInfo.class);
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String sha256 = uploaded.getBody().sha256();
        assertThat(sha256).isEqualTo(Sha256.of(zip));
        assertThat(rest.exchange("/api/library?kind=DATASET", HttpMethod.GET, new HttpEntity<>(user), String.class)
                .getBody()).contains("\"mnist\"");

        // Téléchargement par un agent, repris au 10e octet, comme après une coupure.
        HttpHeaders agent = new HttpHeaders();
        agent.setBearerAuth(AGENT_TOKEN);
        agent.set(HttpHeaders.RANGE, "bytes=10-");
        ResponseEntity<byte[]> rest10 = rest.exchange("/api/files/" + sha256, HttpMethod.GET,
                new HttpEntity<>(agent), byte[].class);
        assertThat(rest10.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(rest10.getHeaders().getFirst("X-File-Sha256")).isEqualTo(sha256);
        assertThat(rest10.getBody()).isEqualTo(Arrays.copyOfRange(zip, 10, zip.length));

        // Sans le jeton des agents, rien n'est téléchargeable.
        assertThat(rest.getForEntity("/api/files/" + sha256, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static byte[] zipOf(String name, String content) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    @Test
    void theRestApiAnswersWithProblemDetailsAndTheHealthEndpointIsUp() {
        assertThat(rest.getForObject("/actuator/health", String.class)).contains("\"status\":\"UP\"");
        assertThat(rest.getForObject("/", String.class)).as("dashboard page").contains("ComputeHive — tableau de bord");
        assertThat(rest.getForObject("/api/dashboard", String.class)).contains("\"jobsByStatus\"");

        ResponseEntity<String> unknown = rest.getForEntity("/api/jobs/ghost", String.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody()).contains("Job not found: ghost");
        assertThat(unknown.getHeaders().getFirst("X-Correlation-Id")).isNotBlank();
    }
}
