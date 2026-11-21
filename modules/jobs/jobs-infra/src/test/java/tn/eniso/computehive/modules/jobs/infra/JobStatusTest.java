package tn.eniso.computehive.modules.jobs.infra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static tn.eniso.computehive.modules.jobs.infra.JobStatus.CANCELLED;
import static tn.eniso.computehive.modules.jobs.infra.JobStatus.DONE;
import static tn.eniso.computehive.modules.jobs.infra.JobStatus.FAILED;
import static tn.eniso.computehive.modules.jobs.infra.JobStatus.PENDING;
import static tn.eniso.computehive.modules.jobs.infra.JobStatus.REASSIGNED;
import static tn.eniso.computehive.modules.jobs.infra.JobStatus.RUNNING;

class JobStatusTest {

    private static Set<JobStatus> nextOf(JobStatus status) {
        return Arrays.stream(JobStatus.values())
                .filter(status::canMoveTo)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(JobStatus.class)));
    }

    @Test
    void allowedTransitions() {
        assertThat(nextOf(PENDING)).containsExactlyInAnyOrder(RUNNING, CANCELLED);
        assertThat(nextOf(RUNNING)).containsExactlyInAnyOrder(DONE, FAILED, CANCELLED, REASSIGNED);
        assertThat(nextOf(REASSIGNED)).containsExactlyInAnyOrder(RUNNING, FAILED, CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = {"DONE", "FAILED", "CANCELLED"})
    void terminalStatusesAreFinal(JobStatus terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        assertThat(nextOf(terminal)).isEmpty();
    }

    @Test
    void pendingAndReassignedJobsWaitForAMachine() {
        assertThat(Arrays.stream(JobStatus.values()).filter(JobStatus::isWaitingForMachine))
                .containsExactlyInAnyOrder(PENDING, REASSIGNED);
    }
}
