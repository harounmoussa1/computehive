package tn.eniso.computehive.simulator;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.eniso.computehive.contracts.worker.v1.AgentMessage;
import tn.eniso.computehive.contracts.worker.v1.FileKind;
import tn.eniso.computehive.contracts.worker.v1.JobFile;
import tn.eniso.computehive.contracts.worker.v1.JobParams;
import tn.eniso.computehive.contracts.worker.v1.LaunchJob;
import tn.eniso.computehive.contracts.worker.v1.Outcome;
import tn.eniso.computehive.contracts.worker.v1.RunningJob;
import tn.eniso.computehive.contracts.worker.v1.ServerCommand;
import tn.eniso.computehive.contracts.worker.v1.StopJob;
import tn.eniso.computehive.contracts.worker.v1.StopReason;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Le faux agent face à un faux serveur : il doit respecter worker.proto et docs/worker-protocol.md. */
class SimulatedAgentTest {

    private static final Behaviour FAST = Behaviour.normal()
            .withEpochDuration(Duration.ofMillis(20))
            .withMaxReconnectDelay(Duration.ofSeconds(1));

    private final FakeGateway gateway = new FakeGateway();
    private Server server;
    private ManagedChannel channel;
    private SimulatedAgent agent;

    @BeforeEach
    void setUp() throws Exception {
        String name = UUID.randomUUID().toString();
        server = InProcessServerBuilder.forName(name).directExecutor().addService(gateway).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterEach
    void tearDown() {
        if (agent != null) {
            agent.close();
        }
        channel.shutdownNow();
        server.shutdownNow();
    }

    private AgentMessage startAgent(Behaviour behaviour) throws InterruptedException {
        agent = new SimulatedAgent(channel, AgentProfile.gpu("a1", "RTX 3060", 12_288), behaviour);
        agent.start();
        return gateway.next();
    }

    private static ServerCommand launch(String commandId, int epochs, int resumeFromEpoch) {
        return ServerCommand.newBuilder().setCommandId(commandId).setLaunch(LaunchJob.newBuilder()
                .setJobId("j-7").setAttemptId(2).setJobType("MNIST_TRAIN")
                .setParams(JobParams.newBuilder().setEpochs(epochs).setLearningRate(0.01).setBatchSize(64))
                .setResumeFromEpoch(resumeFromEpoch)).build();
    }

    private static ServerCommand stop(String commandId, long attemptId) {
        return ServerCommand.newBuilder().setCommandId(commandId).setStop(StopJob.newBuilder()
                .setJobId("j-7").setAttemptId(attemptId).setReason(StopReason.CANCELLED)).build();
    }

    @Test
    void itRegistersWithItsHardwareThenSendsHeartbeats() throws Exception {
        AgentMessage register = startAgent(FAST);

        assertThat(register.getMessageId()).isNotBlank();
        assertThat(register.getRegister().getAgentId()).isEqualTo("a1");
        assertThat(register.getRegister().getGpus(0).getMemoryTotalMb()).isEqualTo(12_288);
        assertThat(register.getRegister().getCapabilities().getCuda()).isTrue();
        assertThat(register.getRegister().getRunningList()).isEmpty();

        AgentMessage heartbeat = gateway.nextHeartbeat();
        assertThat(heartbeat.getHeartbeat().getGpus(0).getMemoryFreeMb()).isEqualTo(12_288);
    }

    @Test
    void itRunsALaunchedJobToTheEndWithProgressCheckpointsAndResult() throws Exception {
        startAgent(FAST);

        gateway.send(launch("cmd-L", 4, 0));

        AgentMessage ack = gateway.next();
        assertThat(ack.getCommandAck().getCommandId()).isEqualTo("cmd-L");
        assertThat(ack.getCommandAck().getAccepted()).isTrue();
        List<AgentMessage> messages = new ArrayList<>();
        AgentMessage message;
        do {
            message = gateway.next();
            messages.add(message);
        } while (!message.hasFinished());

        List<Integer> epochs = messages.stream().filter(AgentMessage::hasProgress)
                .map(m -> m.getProgress().getEpoch()).toList();
        List<Double> losses = messages.stream().filter(AgentMessage::hasProgress)
                .map(m -> m.getProgress().getLoss()).toList();
        assertThat(epochs).containsExactly(1, 2, 3, 4);
        assertThat(losses).isSortedAccordingTo((a, b) -> Double.compare(b, a));
        assertThat(messages.stream().filter(AgentMessage::hasCheckpointSaved).map(m -> m.getCheckpointSaved().getStorageKey()))
                .containsExactly("j-7/attempts/2/epochs/2", "j-7/attempts/2/epochs/4");
        assertThat(messages.stream().filter(AgentMessage::hasCheckpointSaved).map(m -> m.getCheckpointSaved().getSha256()))
                .allSatisfy(sha -> assertThat(sha).matches("[0-9a-f]{64}"));
        assertThat(message.getFinished().getOutcome()).isEqualTo(Outcome.SUCCEEDED);
        assertThat(message.getFinished().getAccuracy()).isBetween(0.8, 1.0);
        assertThat(message.getFinished().getLastCheckpointEpoch()).isEqualTo(4);
    }

    @Test
    void itResumesAfterTheCheckpointEpoch() throws Exception {
        startAgent(FAST);

        gateway.send(launch("cmd-L", 5, 3));

        gateway.next();
        assertThat(gateway.next().getProgress().getEpoch()).isEqualTo(4);
    }

    @Test
    void aResentLaunchIsAcknowledgedButNotRunTwice() throws Exception {
        startAgent(FAST);

        gateway.send(launch("cmd-L", 3, 0));
        gateway.send(launch("cmd-L", 3, 0));

        assertThat(gateway.next().getCommandAck().getAccepted()).isTrue();
        assertThat(gateway.next().getCommandAck().getAccepted()).isTrue();
        List<Integer> epochs = new ArrayList<>();
        AgentMessage message;
        do {
            message = gateway.next();
            if (message.hasProgress()) {
                epochs.add(message.getProgress().getEpoch());
            }
        } while (!message.hasFinished());
        assertThat(epochs).containsExactly(1, 2, 3);
    }

    @Test
    void aSecondJobIsRefusedWhileTheGpuIsBusy() throws Exception {
        startAgent(Behaviour.normal().withEpochDuration(Duration.ofSeconds(10)));

        gateway.send(launch("cmd-1", 3, 0));
        gateway.send(ServerCommand.newBuilder().setCommandId("cmd-2").setLaunch(LaunchJob.newBuilder()
                .setJobId("j-8").setAttemptId(1).setJobType("MNIST_TRAIN")
                .setParams(JobParams.newBuilder().setEpochs(3).setLearningRate(0.01).setBatchSize(64))).build());

        assertThat(gateway.next().getCommandAck().getAccepted()).isTrue();
        AgentMessage refusal = gateway.next();
        assertThat(refusal.getCommandAck().getCommandId()).isEqualTo("cmd-2");
        assertThat(refusal.getCommandAck().getAccepted()).isFalse();
        assertThat(refusal.getCommandAck().getReason()).contains("busy");
    }

    @Test
    void itCanBeToldToRefuseEveryJob() throws Exception {
        startAgent(FAST.rejectingLaunches());

        gateway.send(launch("cmd-L", 3, 0));

        assertThat(gateway.next().getCommandAck().getAccepted()).isFalse();
        assertThat(gateway.quietFor(300)).as("nothing runs").isTrue();
    }

    @Test
    void aStopJobStopsTheTraining() throws Exception {
        startAgent(FAST);
        gateway.send(launch("cmd-L", 1_000, 0));
        gateway.next();
        gateway.next();

        gateway.send(stop("cmd-S", 2));

        AgentMessage message;
        do {
            message = gateway.next();
        } while (!message.hasFinished());
        assertThat(message.getFinished().getOutcome()).isEqualTo(Outcome.STOPPED);
        assertThat(gateway.quietFor(200)).as("no progress after the stop").isTrue();
    }

    @Test
    void aStopJobForAnotherAttemptIsIgnored() throws Exception {
        startAgent(FAST);
        gateway.send(launch("cmd-L", 1_000, 0));
        gateway.next();

        gateway.send(stop("cmd-S", 1));

        AgentMessage message;
        do {
            message = gateway.next();
        } while (!message.hasCommandAck());
        assertThat(message.getCommandAck().getCommandId()).isEqualTo("cmd-S");
        assertThat(gateway.next().hasProgress()).as("training goes on").isTrue();
    }

    @Test
    void aScriptFailureIsReported() throws Exception {
        startAgent(FAST.failingAt(2));
        gateway.send(launch("cmd-L", 5, 0));

        AgentMessage message;
        do {
            message = gateway.next();
        } while (!message.hasFinished());

        assertThat(message.getFinished().getOutcome()).isEqualTo(Outcome.FAILED);
        assertThat(message.getFinished().getError()).contains("epoch 2");
    }

    @Test
    void aCrashedAgentGoesSilentAndDoesNotReconnect() throws Exception {
        startAgent(FAST.crashingAt(2));
        gateway.send(launch("cmd-L", 5, 0));
        gateway.next();
        assertThat(gateway.next().getProgress().getEpoch()).isEqualTo(1);

        assertThat(gateway.nextStreamError()).as("stream cut without a clean close").isNotNull();
        assertThat(gateway.quietFor(1_500)).isTrue();
        assertThat(gateway.streamCount()).isEqualTo(1);
    }

    @Test
    void itReconnectsAndDeclaresItsRunningJobAfterTheServerDropsTheStream() throws Exception {
        startAgent(Behaviour.normal().withEpochDuration(Duration.ofSeconds(30)).withMaxReconnectDelay(Duration.ofSeconds(1)));
        gateway.send(launch("cmd-L", 10, 0));
        gateway.next();

        gateway.dropStream();

        AgentMessage again = gateway.next();
        assertThat(again.hasRegister()).isTrue();
        assertThat(again.getRegister().getRunningList())
                .containsExactly(RunningJob.newBuilder().setJobId("j-7").setAttemptId(2).build());
        assertThat(gateway.streamCount()).isEqualTo(2);
    }

    // ───────────────────────── Ack et outbox ─────────────────────────

    @Test
    void aDurableMessageNotYetAcknowledgedIsSentAgainAfterReconnecting() throws Exception {
        startAgent(Behaviour.normal().withEpochDuration(Duration.ofSeconds(30)).withMaxReconnectDelay(Duration.ofSeconds(1)));
        gateway.stopAcknowledging();
        gateway.send(launch("cmd-L", 10, 0));
        AgentMessage ack = gateway.next();

        gateway.dropStream();

        assertThat(gateway.next().hasRegister()).isTrue();
        AgentMessage resent = gateway.next();
        assertThat(resent.getMessageId()).as("same message, so the server can drop the duplicate")
                .isEqualTo(ack.getMessageId());
        assertThat(resent.getCommandAck().getCommandId()).isEqualTo("cmd-L");
    }

    @Test
    void anAcknowledgedMessageIsNeverSentAgain() throws Exception {
        startAgent(Behaviour.normal().withEpochDuration(Duration.ofSeconds(30)).withMaxReconnectDelay(Duration.ofSeconds(1)));
        gateway.send(launch("cmd-L", 10, 0));
        gateway.next();

        gateway.dropStream();

        assertThat(gateway.next().hasRegister()).isTrue();
        assertThat(gateway.quietFor(500)).as("the CommandAck was acknowledged: nothing to resend").isTrue();
    }

    // ───────────────────────── Fins décidées par l'agent ─────────────────────────

    @Test
    void withoutNewsFromTheServerForLongerThanTheLeaseTheAgentStopsItsJobItself() throws Exception {
        startAgent(FAST);
        gateway.send(launch("cmd-L", 100_000, 0));
        gateway.next();

        gateway.goSilent();

        AgentMessage fenced = gateway.next(AgentMessage::hasFinished);
        assertThat(fenced.getFinished().getOutcome()).isEqualTo(Outcome.FENCED);
        assertThat(fenced.getFinished().getJobId()).isEqualTo("j-7");
        assertThat(gateway.quietFor(300)).as("the job no longer runs").isTrue();
    }

    @Test
    void anAgentRestartedDuringAJobRegistersAgainAndReportsTheJobAsInterrupted() throws Exception {
        startAgent(FAST.restartingAt(3).withMaxReconnectDelay(Duration.ofSeconds(1)));
        gateway.send(launch("cmd-L", 10, 0));

        AgentMessage register = gateway.next(AgentMessage::hasRegister);
        assertThat(register.getRegister().getRunningList()).as("the job process is gone").isEmpty();
        AgentMessage interrupted = gateway.next(AgentMessage::hasFinished);
        assertThat(interrupted.getFinished().getOutcome()).isEqualTo(Outcome.INTERRUPTED);
        assertThat(interrupted.getFinished().getAttemptId()).isEqualTo(2);
        assertThat(interrupted.getFinished().getLastCheckpointEpoch()).isEqualTo(2);
        assertThat(gateway.streamCount()).isEqualTo(2);
    }

    @Test
    void aJobLongerThanItsMaximumRuntimeFails() throws Exception {
        startAgent(FAST);
        gateway.send(ServerCommand.newBuilder().setCommandId("cmd-L").setLaunch(LaunchJob.newBuilder()
                .setJobId("j-7").setAttemptId(1).setEntrypoint("train.py").addFiles(code())
                .setConfigJson("{\"epochs\": 100000}").setMaxRuntimeSeconds(1)).build());

        AgentMessage finished = gateway.next(AgentMessage::hasFinished);

        assertThat(finished.getFinished().getOutcome()).isEqualTo(Outcome.FAILED);
        assertThat(finished.getFinished().getError()).contains("maximum runtime");
    }

    // ───────────────────────── Jobs « code + config + données » ─────────────────────────

    private static JobFile code() {
        return JobFile.newBuilder().setKey("files/abc").setSha256("a".repeat(64)).setSizeBytes(2_048)
                .setKind(FileKind.CODE).setName("projet.zip").build();
    }

    @Test
    void aUserJobTakesItsNumberOfEpochsFromItsConfig() throws Exception {
        startAgent(FAST);

        gateway.send(ServerCommand.newBuilder().setCommandId("cmd-L").setLaunch(LaunchJob.newBuilder()
                .setJobId("j-9").setAttemptId(1).setEntrypoint("src/train.py").addFiles(code())
                .setConfigJson("{\"epochs\": 3, \"learning_rate\": 0.001, \"batch_size\": 64}")).build());

        assertThat(gateway.next().getCommandAck().getAccepted()).isTrue();
        assertThat(gateway.next(AgentMessage::hasProgress).getProgress().getTotalEpochs()).isEqualTo(3);
        assertThat(gateway.next(AgentMessage::hasFinished).getFinished().getOutcome()).isEqualTo(Outcome.SUCCEEDED);
    }

    @Test
    void aUserJobWithoutCodeIsRefused() throws Exception {
        startAgent(FAST);

        gateway.send(ServerCommand.newBuilder().setCommandId("cmd-L").setLaunch(LaunchJob.newBuilder()
                .setJobId("j-9").setAttemptId(1).setEntrypoint("src/train.py").setConfigJson("{}")).build());

        AgentMessage refusal = gateway.next();
        assertThat(refusal.getCommandAck().getAccepted()).isFalse();
        assertThat(refusal.getCommandAck().getReason()).startsWith("no_code");
    }
}
