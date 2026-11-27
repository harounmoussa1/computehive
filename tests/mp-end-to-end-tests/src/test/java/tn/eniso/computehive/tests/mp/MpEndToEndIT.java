package tn.eniso.computehive.tests.mp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tn.eniso.computehive.modules.dashboard.infra.JobDetails;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;
import tn.eniso.computehive.modules.machines.infra.MachineInfo;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;
import tn.eniso.computehive.modules.quotas.infra.UsageInfo;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentInfo;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentStatus;
import tn.eniso.computehive.simulator.AgentProfile;
import tn.eniso.computehive.simulator.Behaviour;
import tn.eniso.computehive.simulator.CheckpointClient;
import tn.eniso.computehive.simulator.SimulatedAgent;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le scénario du jalon, cette fois en <b>M/P</b> : les vrais jars d'{@code app-main} (jobs, machines, quotas) et
 * d'{@code app-scheduling} (scheduling, porte gRPC, checkpoints) tournent dans deux processus, reliés uniquement par
 * leurs API internes REST et par Kafka, comme dans le {@code docker-compose.yml}.
 *
 * <p>Un PC tombe en panne pendant un job : {@code app-main} le déclare mort et publie {@code MachineDead} sur Kafka ;
 * {@code app-scheduling} le reçoit, réattribue le job, qui reprend sur un autre PC depuis son checkpoint. La fin du
 * job revient à {@code app-main} par le proxy de jobs : consommation comptée par quotas, e-mail au propriétaire.</p>
 *
 * <p>Ignoré si Docker n'est pas disponible. Logs des deux applications : {@code target/mp-logs}.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class MpEndToEndIT {

    private static final String AGENT_TOKEN = "mp-agents-token";
    private static final String INTERNAL_TOKEN = "mp-internal-token";
    private static final Duration STARTUP = Duration.ofMinutes(2);
    private static final Duration TIMEOUT = Duration.ofSeconds(90);
    private static final Behaviour FAST = Behaviour.normal()
            .withEpochDuration(Duration.ofMillis(300))
            .withMaxReconnectDelay(Duration.ofSeconds(1));

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.7.1");

    @Container
    static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.21")
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/api/v1/messages").forPort(8025));

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static ApplicationProcess main;
    private static ApplicationProcess scheduling;
    private static int grpcPort;

    private final List<SimulatedAgent> agents = new ArrayList<>();
    private ManagedChannel channel;

    @BeforeAll
    static void startTheTwoApplications() throws Exception {
        int mainPort = freePort();
        int schedulingPort = freePort();
        grpcPort = freePort();
        List<String> shared = List.of(
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--computehive.broker.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
                "--computehive.internal.token=" + INTERNAL_TOKEN);

        // Durées du protocole raccourcies, comme dans ModulithEndToEndIT.
        List<String> mainArguments = new ArrayList<>(shared);
        mainArguments.addAll(List.of(
                "--computehive.remote.scheduling.url=http://localhost:" + schedulingPort,
                "--computehive.notifications.email.host=" + MAILPIT.getHost(),
                "--computehive.notifications.email.port=" + MAILPIT.getMappedPort(1025),
                "--computehive.machines.heartbeat-interval=1s",
                "--computehive.machines.lease-ttl=3s",
                "--computehive.machines.detection-interval=500ms",
                "--computehive.machines.startup-grace=0s"));
        main = ApplicationProcess.start("app-main", mainPort, mainArguments);
        main.awaitHealthy(STARTUP);

        List<String> schedulingArguments = new ArrayList<>(shared);
        schedulingArguments.addAll(List.of(
                "--computehive.remote.jobs.url=" + main.baseUrl(),
                "--computehive.remote.machines.url=" + main.baseUrl(),
                "--computehive.remote.quotas.url=" + main.baseUrl(),
                "--computehive.agents.token=" + AGENT_TOKEN,
                "--computehive.grpc.port=" + grpcPort,
                "--computehive.scheduling.placement-interval=200ms",
                "--computehive.scheduling.reconciliation-interval=1s",
                "--computehive.scheduling.lost-job-grace=3s"));
        scheduling = ApplicationProcess.start("app-scheduling", schedulingPort, schedulingArguments);
        scheduling.awaitHealthy(STARTUP);
    }

    @AfterAll
    static void stopTheTwoApplications() throws Exception {
        if (scheduling != null) {
            scheduling.close();
        }
        if (main != null) {
            main.close();
        }
    }

    @BeforeEach
    void connectAgents() {
        Metadata credentials = new Metadata();
        credentials.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + AGENT_TOKEN);
        channel = NettyChannelBuilder.forAddress("localhost", grpcPort).usePlaintext()
                .intercept(MetadataUtils.newAttachHeadersInterceptor(credentials)).build();
    }

    @AfterEach
    void disconnectAgents() {
        agents.forEach(SimulatedAgent::close);
        channel.shutdownNow();
    }

    @Test
    void aJobSurvivesTheCrashOfItsMachineAcrossTheTwoApplications() throws Exception {
        startAgent("pc-a", FAST.crashingAt(4));
        JobInfo submitted = submitJob(8);

        JobInfo onFirstMachine = awaitJob(submitted.id(), j -> j.status() == JobStatus.RUNNING, "running on pc-a");
        assertThat(onFirstMachine.machine().name()).isEqualTo("sim-pc-a");
        startAgent("pc-b", FAST);

        JobInfo done = awaitJob(submitted.id(), j -> j.status() == JobStatus.DONE, "done after the crash");
        assertThat(done.machine().name()).isEqualTo("sim-pc-b");
        assertThat(done.currentEpoch()).isEqualTo(8);

        // Les tentatives sont dans app-scheduling : lues par son API interne, avec le jeton interne.
        List<AssignmentInfo> attempts = await(() -> attemptsOf(submitted.id()),
                all -> all.size() == 2 && all.get(1).status() == AssignmentStatus.COMPLETED, "second attempt completed");
        assertThat(attempts).extracting(AssignmentInfo::status)
                .containsExactly(AssignmentStatus.LOST, AssignmentStatus.COMPLETED);
        assertThat(attempts.get(1).resumeFrom().epoch()).as("resumed from the checkpoint of epoch 2").isEqualTo(2);
        MachineInfo crashed = get(main, "/api/machines/" + attempts.get(0).machine().id(), null, MachineInfo.class);
        assertThat(crashed.status()).as("declared dead by app-main").isEqualTo(MachineStatus.DEAD);

        // Le tableau de bord d'app-main lit ces tentatives dans app-scheduling, par le proxy de scheduling.
        JobDetails onDashboard = get(main, "/api/dashboard/jobs/" + submitted.id(), null, JobDetails.class);
        assertThat(onDashboard.attemptsAvailable()).isTrue();
        assertThat(onDashboard.attempts()).extracting(AssignmentInfo::status)
                .containsExactly(AssignmentStatus.LOST, AssignmentStatus.COMPLETED);
        assertThat(body(main, "/", null)).as("dashboard page").contains("ComputeHive — tableau de bord");

        // Le checkpoint de reprise a été déposé dans app-scheduling par pc-a, puis relu par pc-b.
        HttpResponse<byte[]> checkpoint = HTTP.send(HttpRequest.newBuilder(URI.create(scheduling.baseUrl()
                        + "/api/checkpoints/" + attempts.get(1).resumeFrom().checkpointKey()))
                .header("Authorization", "Bearer " + AGENT_TOKEN).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(checkpoint.statusCode()).isEqualTo(200);
        assertThat(checkpoint.headers().firstValue("X-Checkpoint-Sha256"))
                .contains(attempts.get(1).resumeFrom().checkpointSha256());

        // La fin du job, arrivée par le proxy de jobs, déclenche dans app-main quotas et la notification.
        UsageInfo usage = await(() -> get(main, "/api/quotas/u-1", null, UsageInfo.class),
                u -> u.jobsCompleted() == 1, "usage recorded");
        assertThat(usage.gpuSeconds()).isPositive();
        String inbox = await(MpEndToEndIT::inbox, mails -> mails.contains("mnist-mp"), "owner notified by e-mail");
        assertThat(inbox).contains("u-1@computehive.local");

        // Chaque application publie les métriques de ses modules, et l'état de ses circuits vers l'autre (ADR 0014).
        String mainMetrics = await(() -> body(main, "/actuator/prometheus", null),
                m -> metric(m, "computehive_jobs_finished_total", "status=\"done\"") >= 1, "metrics of app-main");
        assertThat(metric(mainMetrics, "computehive_machines_failures_total")).isGreaterThanOrEqualTo(1);
        assertThat(metric(mainMetrics, "resilience4j_circuitbreaker_state", "name=\"scheduling\"", "state=\"closed\""))
                .isEqualTo(1);
        String schedulingMetrics = body(scheduling, "/actuator/prometheus", null);
        assertThat(metric(schedulingMetrics, "computehive_attempts_started_total", "start=\"checkpoint\""))
                .isGreaterThanOrEqualTo(1);
        assertThat(metric(schedulingMetrics, "computehive_attempts_ended_total", "outcome=\"lost\""))
                .isGreaterThanOrEqualTo(1);
        assertThat(metric(schedulingMetrics, "resilience4j_circuitbreaker_state", "name=\"jobs\"", "state=\"closed\""))
                .isEqualTo(1);
        assertThat(schedulingMetrics).as("jobs runs in app-main only").doesNotContain("computehive_jobs_active");
    }

    @Test
    void theInternalApiAnswersOnlyToTheOtherApplication() throws Exception {
        assertThat(status(main, "/internal/jobs", null)).as("a user").isEqualTo(401);
        assertThat(status(main, "/internal/jobs", AGENT_TOKEN)).as("an agent of the laboratory").isEqualTo(401);
        assertThat(status(scheduling, "/internal/scheduling/jobs/j-1/attempts", AGENT_TOKEN)).isEqualTo(401);

        assertThat(status(main, "/internal/jobs", INTERNAL_TOKEN)).as("app-scheduling").isEqualTo(200);
        assertThat(status(scheduling, "/internal/scheduling/jobs/j-1/attempts", INTERNAL_TOKEN)).isEqualTo(200);
    }

    // ───────────── Outils ─────────────

    private void startAgent(String agentId, Behaviour behaviour) {
        SimulatedAgent agent = new SimulatedAgent(channel, AgentProfile.gpu(agentId, "RTX 3060", 12_288), behaviour,
                new CheckpointClient(scheduling.baseUrl(), AGENT_TOKEN));
        agents.add(agent);
        agent.start();
    }

    private static JobInfo submitJob(int epochs) throws Exception {
        String body = """
                {"name": "mnist-mp", "type": "MNIST_TRAIN",
                 "params": {"epochs": %d, "learningRate": 0.01, "batchSize": 64},
                 "requirements": {"gpu": true, "minGpuMemoryMb": 4096}}
                """.formatted(epochs);
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(main.baseUrl() + "/api/jobs"))
                .header("Content-Type", "application/json")
                .header("X-User-Id", "u-1")
                .header("X-User-Name", "Haroun")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return JSON.readValue(response.body(), JobInfo.class);
    }

    private static JobInfo awaitJob(String id, Predicate<JobInfo> condition, String description) throws Exception {
        return await(() -> get(main, "/api/jobs/" + id, null, JobInfo.class), condition, "job " + description);
    }

    private static List<AssignmentInfo> attemptsOf(String jobId) throws Exception {
        return JSON.readValue(body(scheduling, "/internal/scheduling/jobs/" + jobId + "/attempts", INTERNAL_TOKEN),
                new TypeReference<>() {
                });
    }

    private static <T> T get(ApplicationProcess app, String path, String token, Class<T> type) throws Exception {
        return JSON.readValue(body(app, path, token), type);
    }

    private static String body(ApplicationProcess app, String path, String token) throws Exception {
        HttpResponse<String> response = HTTP.send(request(app, path, token), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("GET %s: %s", path, response.body()).isEqualTo(200);
        return response.body();
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

    private static int status(ApplicationProcess app, String path, String token) throws Exception {
        return HTTP.send(request(app, path, token), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static HttpRequest request(ApplicationProcess app, String path, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(app.baseUrl() + path));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return request.build();
    }

    private static String inbox() throws Exception {
        URI uri = URI.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + "/api/v1/messages");
        return HTTP.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    @FunctionalInterface
    private interface State<T> {
        T read() throws Exception;
    }

    /**
     * Relit {@code state} jusqu'à ce qu'il vérifie {@code condition} : chaque effet traverse un module, puis parfois
     * l'autre application (REST ou Kafka), dans sa propre transaction. Le test attend donc chacun d'eux.
     */
    private static <T> T await(State<T> state, Predicate<T> condition, String description) throws Exception {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        T value = state.read();
        while (!condition.test(value)) {
            assertThat(System.nanoTime()).as("%s within %s, last state: %s", description, TIMEOUT, value)
                    .isLessThan(deadline);
            Thread.sleep(200);
            value = state.read();
        }
        return value;
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
