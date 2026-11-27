package tn.eniso.computehive.modules.scheduling.ws.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tn.eniso.computehive.contracts.worker.v1.AgentMessage;
import tn.eniso.computehive.contracts.worker.v1.CheckpointSaved;
import tn.eniso.computehive.contracts.worker.v1.CommandAck;
import tn.eniso.computehive.contracts.worker.v1.Finished;
import tn.eniso.computehive.contracts.worker.v1.GpuSample;
import tn.eniso.computehive.contracts.worker.v1.Heartbeat;
import tn.eniso.computehive.contracts.worker.v1.Outcome;
import tn.eniso.computehive.contracts.worker.v1.Progress;
import tn.eniso.computehive.contracts.worker.v1.Register;
import tn.eniso.computehive.contracts.worker.v1.RunningJob;
import tn.eniso.computehive.contracts.worker.v1.ServerCommand;
import tn.eniso.computehive.contracts.worker.v1.StopReason;
import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.exception.RemoteModuleUnavailableException;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentCommand;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentHeartbeat;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentHello;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentRegistered;
import tn.eniso.computehive.modules.scheduling.infra.agent.CheckpointReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.CommandAckReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.FinishedReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.MessageOutcome;
import tn.eniso.computehive.modules.scheduling.infra.agent.ProgressReport;
import tn.eniso.computehive.modules.scheduling.infra.agent.RunningAttempt;
import tn.eniso.computehive.modules.scheduling.service.api.SchedulingModule;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Teste la porte gRPC sur un vrai serveur gRPC (en mémoire) avec un faux agent : traduction des
 * messages en appels de la façade, ordres envoyés à l'agent, cycle de vie du stream.
 */
class WorkerGatewayServiceTest {

    /** Fausse façade : chaque message d'agent est accepté, sauf réponse contraire du test. */
    private final SchedulingModule scheduling = Mockito.mock(SchedulingModule.class, call ->
            call.getMethod().getReturnType() == MessageOutcome.class
                    ? MessageOutcome.ACCEPTED
                    : Mockito.RETURNS_DEFAULTS.answer(call));
    private final AgentSessionRegistry sessions = new AgentSessionRegistry();
    private final MeterRegistry meters = new SimpleMeterRegistry();
    private Server server;
    private ManagedChannel channel;

    @BeforeEach
    void setUp() throws Exception {
        when(scheduling.registerAgent(any())).thenAnswer(call -> new AgentRegistered(
                "machine-" + call.<AgentHello>getArgument(0).agentId(), Duration.ofSeconds(10), Duration.ofSeconds(30)));
        String name = UUID.randomUUID().toString();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new WorkerGatewayService(scheduling, sessions, new GatewayMetrics(meters, sessions)))
                .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    private TestAgent connected(String agentId) throws InterruptedException {
        TestAgent agent = new TestAgent(channel);
        agent.send(AgentMessage.newBuilder().setRegister(TestAgent.register(agentId)));
        assertThat(agent.next().hasRegistered()).isTrue();
        return agent;
    }

    // ───────────────────────── Inscription ─────────────────────────

    @Test
    void registerIsAnsweredWithTheMachineIdAndTheLeaseRules() throws Exception {
        TestAgent agent = new TestAgent(channel);

        agent.send(AgentMessage.newBuilder().setRegister(TestAgent.register("a1")));

        ServerCommand reply = agent.next();
        assertThat(reply.getCommandId()).isNotBlank();
        assertThat(reply.getRegistered().getMachineId()).isEqualTo("machine-a1");
        assertThat(reply.getRegistered().getHeartbeatIntervalSeconds()).isEqualTo(10);
        assertThat(reply.getRegistered().getLeaseTtlSeconds()).isEqualTo(30);
        assertThat(sessions.isConnected("machine-a1")).isTrue();

        ArgumentCaptor<AgentHello> hello = ArgumentCaptor.forClass(AgentHello.class);
        verify(scheduling).registerAgent(hello.capture());
        assertThat(hello.getValue().gpu()).isEqualTo(new AgentHello.AgentGpu("RTX 3060", 12_288, true));
        assertThat(hello.getValue().cpuCores()).isEqualTo(8);
    }

    @Test
    void theStreamMustStartWithRegister() throws Exception {
        TestAgent agent = new TestAgent(channel);

        agent.send(AgentMessage.newBuilder().setHeartbeat(Heartbeat.getDefaultInstance()));

        assertThat(agent.closedStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
        verify(scheduling, never()).heartbeat(anyString(), any());
    }

    @Test
    void anInvalidRegisterIsRefused() throws Exception {
        TestAgent agent = new TestAgent(channel);

        agent.send(AgentMessage.newBuilder().setRegister(Register.newBuilder().setName("no agent id")));

        assertThat(agent.closedStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void whenTheMachinesModuleIsUnreachableTheAgentIsToldToRetryLater() throws Exception {
        doThrow(new RemoteModuleUnavailableException("machines", "timeout")).when(scheduling).registerAgent(any());
        TestAgent agent = new TestAgent(channel);

        agent.send(AgentMessage.newBuilder().setRegister(Register.newBuilder().setAgentId("agent-a").setName("pc-a")));

        assertThat(agent.closedStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
    }

    @Test
    void resynchronizationHappensAfterRegisteredSoItsStopJobReachesTheAgent() throws Exception {
        doAnswer(call -> sessions.send(call.getArgument(0),
                new AgentCommand.StopJob("cmd-stop", "old-job", 1, AgentCommand.StopReason.STALE_ATTEMPT, false)))
                .when(scheduling).resynchronize(eq("machine-a1"), any());
        TestAgent agent = new TestAgent(channel);

        agent.send(AgentMessage.newBuilder().setRegister(TestAgent.register("a1",
                RunningJob.newBuilder().setJobId("old-job").setAttemptId(1).build())));

        assertThat(agent.next().hasRegistered()).isTrue();
        ServerCommand stop = agent.next();
        assertThat(stop.getStop().getJobId()).isEqualTo("old-job");
        verify(scheduling).resynchronize("machine-a1", List.of(new RunningAttempt("old-job", 1)));
    }

    // ───────────────────────── Agent → façade ─────────────────────────

    @Test
    void eachMessageBecomesOneFacadeCallWithItsMessageId() throws Exception {
        TestAgent agent = connected("a1");

        agent.send(AgentMessage.newBuilder().setHeartbeat(Heartbeat.newBuilder()
                .addGpus(GpuSample.newBuilder().setMemoryFreeMb(8_000).setUtilizationPercent(90))
                .addRunning(RunningJob.newBuilder().setJobId("j-7").setAttemptId(2))));
        String progressId = agent.send(AgentMessage.newBuilder().setProgress(Progress.newBuilder()
                .setJobId("j-7").setAttemptId(2).setEpoch(3).setTotalEpochs(10).setLoss(0.42).setPercent(30)));
        String checkpointId = agent.send(AgentMessage.newBuilder().setCheckpointSaved(CheckpointSaved.newBuilder()
                .setJobId("j-7").setAttemptId(2).setEpoch(3).setStorageKey("j-7/attempts/2/epochs/3")
                .setSha256("a".repeat(64)).setSizeBytes(1_024)));
        String finishedId = agent.send(AgentMessage.newBuilder().setFinished(Finished.newBuilder()
                .setJobId("j-7").setAttemptId(2).setOutcome(Outcome.SUCCEEDED).setAccuracy(0.98)
                .setError("ignored on success").setLastCheckpointEpoch(9)));
        String ackId = agent.send(AgentMessage.newBuilder().setCommandAck(CommandAck.newBuilder()
                .setCommandId("cmd-A").setAccepted(false).setReason("GPU busy")));

        verify(scheduling).heartbeat("machine-a1", new AgentHeartbeat(8_000L, 90, List.of(new RunningAttempt("j-7", 2))));
        verify(scheduling).reportProgress("machine-a1", progressId, new ProgressReport("j-7", 2, 3, 10, 0.42, 30));
        verify(scheduling).reportCheckpoint("machine-a1", checkpointId,
                new CheckpointReport("j-7", 2, 3, "j-7/attempts/2/epochs/3", "a".repeat(64), 1_024));
        verify(scheduling).reportFinished("machine-a1", finishedId,
                new FinishedReport("j-7", 2, FinishedReport.Outcome.SUCCEEDED, 0.98, null, 9));
        verify(scheduling).acknowledge("machine-a1", ackId, new CommandAckReport("cmd-A", false, "GPU busy"));
    }

    @Test
    void aCpuWorkerHasNoGpuMeasures() throws Exception {
        TestAgent agent = connected("a1");

        agent.send(AgentMessage.newBuilder().setHeartbeat(Heartbeat.getDefaultInstance()));

        verify(scheduling).heartbeat("machine-a1", new AgentHeartbeat(null, null, List.of()));
    }

    @Test
    void aRejectedMessageDoesNotCloseTheStream() throws Exception {
        when(scheduling.reportProgress(anyString(), anyString(), any()))
                .thenThrow(new ConflictException("job not running"));
        TestAgent agent = connected("a1");

        agent.send(AgentMessage.newBuilder().setProgress(Progress.newBuilder().setJobId("j-7").setAttemptId(1)));
        agent.send(AgentMessage.newBuilder().setFinished(Finished.newBuilder().setJobId("j-7").setAttemptId(1)));
        agent.send(AgentMessage.newBuilder().setHeartbeat(Heartbeat.getDefaultInstance()));

        assertThat(agent.isOpen()).isTrue();
        verify(scheduling, never()).reportFinished(anyString(), anyString(), any());
        verify(scheduling).heartbeat(eq("machine-a1"), any());
    }

    @Test
    void aHeartbeatFromAnUnknownMachineClosesTheStreamSoTheAgentRegistersAgain() throws Exception {
        when(scheduling.heartbeat(anyString(), any())).thenThrow(new NotFoundException("Machine", "machine-a1"));
        TestAgent agent = connected("a1");

        agent.send(AgentMessage.newBuilder().setHeartbeat(Heartbeat.getDefaultInstance()));

        assertThat(agent.closedStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(sessions.isConnected("machine-a1")).isFalse();
    }

    // ───────────────────────── Métriques ─────────────────────────

    private double messages(String type, String outcome) {
        var counter = meters.find("computehive.agent.messages").tags("type", type, "outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void eachMessageIsCountedWithTheOutcomeGivenByTheFacade() throws Exception {
        when(scheduling.reportProgress(anyString(), anyString(), any())).thenReturn(MessageOutcome.STALE);
        when(scheduling.reportFinished(anyString(), anyString(), any()))
                .thenThrow(new ConflictException("job not running"));
        TestAgent agent = connected("a1");

        agent.send(AgentMessage.newBuilder().setHeartbeat(Heartbeat.getDefaultInstance()));
        agent.send(AgentMessage.newBuilder().setProgress(Progress.newBuilder().setJobId("j-7").setAttemptId(1)));
        agent.send(AgentMessage.newBuilder().setFinished(Finished.newBuilder().setJobId("j-7").setAttemptId(1)));

        assertThat(messages("register", "accepted")).isEqualTo(1);
        assertThat(messages("heartbeat", "accepted")).isEqualTo(1);
        assertThat(messages("progress", "stale")).isEqualTo(1);
        assertThat(messages("finished", "rejected")).isEqualTo(1);
        assertThat(meters.get("computehive.agents.connected").gauge().value()).isEqualTo(1);
    }

    @Test
    void aRefusedRegistrationIsCountedWithItsCause() throws Exception {
        doThrow(new RemoteModuleUnavailableException("machines", "timeout")).when(scheduling).registerAgent(any());
        TestAgent agent = new TestAgent(channel);

        agent.send(AgentMessage.newBuilder().setRegister(Register.newBuilder().setAgentId("agent-a").setName("pc-a")));

        assertThat(agent.closedStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        assertThat(messages("register", "unavailable")).isEqualTo(1);
        assertThat(meters.get("computehive.agents.connected").gauge().value()).isZero();
    }

    // ───────────────────────── Module → agent ─────────────────────────

    @Test
    void launchAndStopOrdersReachTheAgent() throws Exception {
        TestAgent agent = connected("a1");

        boolean launched = sessions.send("machine-a1", new AgentCommand.LaunchJob("cmd-L", "j-7", 1, "MNIST_TRAIN",
                10, 0.01, 64, 0, null, null));
        boolean stopped = sessions.send("machine-a1",
                new AgentCommand.StopJob("cmd-S", "j-6", 3, AgentCommand.StopReason.CANCELLED, true));

        assertThat(launched).isTrue();
        assertThat(stopped).isTrue();
        ServerCommand launch = agent.next();
        assertThat(launch.getCommandId()).isEqualTo("cmd-L");
        assertThat(launch.getLaunch().getJobType()).isEqualTo("MNIST_TRAIN");
        assertThat(launch.getLaunch().getParams().getLearningRate()).isEqualTo(0.01);
        assertThat(launch.getLaunch().getResumeFromEpoch()).isZero();
        assertThat(launch.getLaunch().getResumeCheckpointKey()).isEmpty();
        ServerCommand stop = agent.next();
        assertThat(stop.getStop().getReason()).isEqualTo(StopReason.CANCELLED);
        assertThat(stop.getStop().getCheckpointFirst()).isTrue();
    }

    @Test
    void anOrderForADisconnectedMachineIsNotSent() {
        assertThat(sessions.send("machine-ghost",
                new AgentCommand.StopJob("cmd", "j", 1, AgentCommand.StopReason.STALE_ATTEMPT, false))).isFalse();
    }

    // ───────────────────────── Cycle de vie du stream ─────────────────────────

    @Test
    void aReconnectionReplacesThePreviousStream() throws Exception {
        TestAgent first = connected("a1");
        TestAgent second = connected("a1");

        assertThat(first.closedStatus().getCode()).isEqualTo(Status.Code.ABORTED);
        sessions.send("machine-a1", new AgentCommand.StopJob("cmd", "j", 1, AgentCommand.StopReason.STALE_ATTEMPT, false));
        assertThat(second.next().getCommandId()).isEqualTo("cmd");
        assertThat(sessions.connectedCount()).isEqualTo(1);
    }

    @Test
    void closingTheStreamDisconnectsTheMachine() throws Exception {
        TestAgent agent = connected("a1");

        agent.close();

        assertThat(agent.closedStatus().isOk()).isTrue();
        assertThat(sessions.isConnected("machine-a1")).isFalse();
    }
}
