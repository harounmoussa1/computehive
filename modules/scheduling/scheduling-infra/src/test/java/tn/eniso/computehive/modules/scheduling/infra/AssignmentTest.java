package tn.eniso.computehive.modules.scheduling.infra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.core.infra.ref.JobRef;
import tn.eniso.computehive.core.infra.ref.MachineRef;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssignmentTest {

    private static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");
    private static final JobRef JOB = new JobRef("j-7", "mnist", "MNIST_TRAIN");
    private static final MachineRef PC_12 = new MachineRef("m-1", "labo-pc-12", "GPU");

    private static Assignment launching() {
        return Assignment.launch("a-1", JOB, PC_12, 1, ResumePoint.FROM_SCRATCH, "cmd-A", T0);
    }

    @Test
    void aNewAttemptWaitsForTheAgentAcknowledgement() {
        Assignment assignment = launching();

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.LAUNCHING);
        assertThat(assignment.isActive()).isTrue();
        assertThat(assignment.getLaunchSendCount()).isEqualTo(1);
        assertThat(assignment.getLaunchSentAt()).isEqualTo(T0);
    }

    @Test
    void attemptsStartAtOne() {
        assertThatThrownBy(() -> Assignment.launch("a-1", JOB, PC_12, 0, ResumePoint.FROM_SCRATCH, "cmd-A", T0))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void confirmRunningIsIdempotent() {
        Assignment assignment = launching();

        assertThat(assignment.confirmRunning()).isTrue();
        assertThat(assignment.confirmRunning()).isFalse();
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.RUNNING);
    }

    @Test
    void aRefusedLaunchIsRejectedButARunningAttemptCannotBe() {
        Assignment refused = launching();
        refused.reject("GPU already busy", T0.plusSeconds(1));
        assertThat(refused.getStatus()).isEqualTo(AssignmentStatus.REJECTED);
        assertThat(refused.getEndReason()).isEqualTo("GPU already busy");
        assertThat(refused.isActive()).isFalse();

        Assignment running = launching();
        running.confirmRunning();
        assertThatThrownBy(() -> running.reject("late refusal", T0)).isInstanceOf(ConflictException.class);
    }

    @Test
    void anAttemptCanFinishEvenIfItsAcknowledgementWasLost() {
        Assignment assignment = launching();

        assignment.complete(T0.plusSeconds(600));

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        assertThat(assignment.getEndedAt()).isEqualTo(T0.plusSeconds(600));
    }

    @Test
    void onlyAPendingLaunchCanBeResent() {
        Assignment assignment = launching();

        assignment.recordLaunchResent(T0.plusSeconds(15));
        assertThat(assignment.getLaunchSendCount()).isEqualTo(2);
        assertThat(assignment.getLaunchSentAt()).isEqualTo(T0.plusSeconds(15));

        assignment.confirmRunning();
        assertThatThrownBy(() -> assignment.recordLaunchResent(T0)).isInstanceOf(ConflictException.class);
    }

    @ParameterizedTest
    @EnumSource(value = AssignmentStatus.class, names = {"COMPLETED", "FAILED", "STOPPED", "REJECTED", "LOST"})
    void endedAttemptsAreFinal(AssignmentStatus ended) {
        assertThat(ended.isActive()).isFalse();
        assertThat(Arrays.stream(AssignmentStatus.values()).filter(ended::canMoveTo)).isEmpty();
    }

    @Test
    void aLostAttemptCannotComeBack() {
        Assignment assignment = launching();
        assignment.confirmRunning();
        assignment.lose("machine labo-pc-12 is DEAD", T0.plusSeconds(40));

        assertThatThrownBy(assignment::confirmRunning).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> assignment.complete(T0)).isInstanceOf(ConflictException.class);
    }

    @Test
    void aVeryLongEndReasonIsTruncated() {
        Assignment assignment = launching();

        assignment.fail("x".repeat(5_000), T0);

        assertThat(assignment.getEndReason()).hasSize(Assignment.MAX_END_REASON_LENGTH);
    }

    @Test
    void resumePointsAreConsistent() {
        assertThat(ResumePoint.FROM_SCRATCH.fromScratch()).isTrue();
        assertThat(new ResumePoint(4, "j-7/attempts/1/epochs/4", "a".repeat(64)).fromScratch()).isFalse();
        assertThatThrownBy(() -> new ResumePoint(4, null, null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new ResumePoint(0, "key", "a".repeat(64))).isInstanceOf(ValidationException.class);
    }

    @Test
    void copyIsIndependent() {
        Assignment assignment = launching();
        Assignment copy = assignment.copy();

        assignment.confirmRunning();

        assertThat(copy.getStatus()).isEqualTo(AssignmentStatus.LAUNCHING);
    }
}
