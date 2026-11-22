package tn.eniso.computehive.contracts.remote;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tn.eniso.computehive.core.infra.ref.JobRef;
import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentInfo;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentStatus;
import tn.eniso.computehive.modules.scheduling.infra.ResumePoint;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentHeartbeat;
import tn.eniso.computehive.modules.scheduling.service.api.SchedulingModule;
import tn.eniso.computehive.modules.scheduling.service.restcli.SchedulingModuleRestClient;
import tn.eniso.computehive.modules.scheduling.ws.rest.SchedulingInternalController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SchedulingRemoteContractTest {

    private static final Instant T0 = Instant.parse("2026-11-22T10:00:00Z");
    private static final JobRef JOB = new JobRef("j-1", "mnist", "MNIST_TRAIN");

    private final SchedulingModule facade = Mockito.mock(SchedulingModule.class);
    private final SchedulingModule proxy = new SchedulingModuleRestClient(
            RemoteContract.client("scheduling", new SchedulingInternalController(facade)));

    private static AssignmentInfo attempt(long attemptId, String machine, AssignmentStatus status,
                                          ResumePoint resumeFrom) {
        return new AssignmentInfo("a-" + attemptId, JOB, new MachineRef("m-" + attemptId, machine, "GPU"), attemptId,
                status, resumeFrom, T0.plusSeconds(attemptId), T0, status.isActive() ? null : T0.plusSeconds(60),
                status.isActive() ? null : "machine dead");
    }

    @Test
    void theAttemptsOfAJobCanBeReadFromAnotherApplication() {
        AssignmentInfo lost = attempt(1, "pc-a", AssignmentStatus.LOST, null);
        AssignmentInfo resumed = attempt(2, "pc-b", AssignmentStatus.RUNNING,
                new ResumePoint(2, "j-1/attempts/1/epochs/2", "b".repeat(64)));
        when(facade.attemptsOf("j-1")).thenReturn(List.of(lost, resumed));
        when(facade.currentAttempt("j-1")).thenReturn(Optional.of(resumed));
        when(facade.currentAttempt("j-done")).thenReturn(Optional.empty());

        assertThat(proxy.attemptsOf("j-1")).containsExactly(lost, resumed);
        assertThat(proxy.currentAttempt("j-1")).contains(resumed);
        assertThat(proxy.currentAttempt("j-done")).isEmpty();
    }

    @Test
    void agentOperationsAreNeverForwarded() {
        assertThatThrownBy(() -> proxy.heartbeat("m-1", new AgentHeartbeat(null, null, List.of())))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("gRPC gateway");
        verifyNoInteractions(facade);
    }
}
