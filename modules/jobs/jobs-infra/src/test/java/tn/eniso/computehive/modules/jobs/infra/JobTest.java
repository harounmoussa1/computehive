package tn.eniso.computehive.modules.jobs.infra;

import org.junit.jupiter.api.Test;
import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.core.infra.ref.UserRef;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobTest {

    private static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");
    private static final MachineRef PC_12 = new MachineRef("m-1", "labo-pc-12", "GPU");
    private static final MachineRef PC_15 = new MachineRef("m-2", "labo-pc-15", "GPU");
    private static final String SHA = "a".repeat(64);

    private static Job pendingJob() {
        return Job.submit("j-1", new JobSubmission(new UserRef("u-1", "Haroun", "MEMBER"), "mnist-lr-0.01",
                JobType.MNIST_TRAIN, new JobParams(10, 0.01, 64), JobRequirements.gpu(4_096)), T0);
    }

    private static Job runningJob() {
        Job job = pendingJob();
        job.start(PC_12, T0.plusSeconds(5));
        return job;
    }

    private static JobCheckpoint checkpoint(int epoch) {
        return new JobCheckpoint(epoch, "j-1/attempts/1/epochs/" + epoch, SHA);
    }

    @Test
    void submissionCreatesAPendingJobWithoutMachine() {
        Job job = pendingJob();

        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getMachine()).isNull();
        assertThat(job.getSubmittedAt()).isEqualTo(T0);
        assertThat(job.getProgress()).isZero();
        assertThat(job.lastCheckpointEpoch()).isZero();
        assertThat(job.toInfo().toRef().type()).isEqualTo("MNIST_TRAIN");
    }

    @Test
    void startAssignsTheMachine() {
        Job job = runningJob();

        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.getMachine()).isEqualTo(PC_12);
        assertThat(job.getStartedAt()).isEqualTo(T0.plusSeconds(5));
    }

    @Test
    void progressIsRecordedOnlyWhileRunning() {
        Job job = pendingJob();
        assertThatThrownBy(() -> job.reportProgress(new JobProgress(1, 0.9, 10)))
                .isInstanceOf(ConflictException.class);

        job.start(PC_12, T0);
        job.reportProgress(new JobProgress(3, 0.42, 30));

        assertThat(job.getCurrentEpoch()).isEqualTo(3);
        assertThat(job.getLastLoss()).isEqualTo(0.42);
        assertThat(job.getProgress()).isEqualTo(30);
    }

    @Test
    void progressCannotGoBeyondTheRequestedEpochs() {
        Job job = runningJob();

        assertThatThrownBy(() -> job.reportProgress(new JobProgress(11, 0.1, 100)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void checkpointsOnlyMoveForward() {
        Job job = runningJob();

        assertThat(job.recordCheckpoint(checkpoint(2))).isTrue();
        assertThat(job.recordCheckpoint(checkpoint(2))).isFalse();
        assertThatThrownBy(() -> job.recordCheckpoint(checkpoint(1))).isInstanceOf(ConflictException.class);
        assertThat(job.recordCheckpoint(checkpoint(4))).isTrue();

        assertThat(job.lastCheckpointEpoch()).isEqualTo(4);
    }

    @Test
    void reassignmentResumesFromTheLastCheckpointOnAnotherMachine() {
        Job job = runningJob();
        job.recordCheckpoint(checkpoint(4));
        job.reportProgress(new JobProgress(6, 0.3, 60));

        job.reassign();

        assertThat(job.getStatus()).isEqualTo(JobStatus.REASSIGNED);
        assertThat(job.getMachine()).isNull();
        assertThat(job.getCurrentEpoch()).isEqualTo(4);
        assertThat(job.getProgress()).isEqualTo(40);

        job.start(PC_15, T0.plusSeconds(60));

        assertThat(job.getMachine()).isEqualTo(PC_15);
        assertThat(job.getStartedAt()).as("first start is kept").isEqualTo(T0.plusSeconds(5));
    }

    @Test
    void reassignmentWithoutCheckpointRestartsFromZero() {
        Job job = runningJob();
        job.reportProgress(new JobProgress(2, 0.5, 20));

        job.reassign();

        assertThat(job.getCurrentEpoch()).isZero();
        assertThat(job.getProgress()).isZero();
    }

    @Test
    void completionStoresTheResult() {
        Job job = runningJob();

        job.complete(0.98, T0.plusSeconds(600));

        assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(job.getAccuracy()).isEqualTo(0.98);
        assertThat(job.getProgress()).isEqualTo(100);
        assertThat(job.getCurrentEpoch()).isEqualTo(10);
        assertThat(job.getFinishedAt()).isEqualTo(T0.plusSeconds(600));
        assertThat(job.getMachine()).isEqualTo(PC_12);
    }

    @Test
    void aPendingJobCannotCompleteOrFail() {
        Job job = pendingJob();

        assertThatThrownBy(() -> job.complete(0.9, T0)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> job.fail("boom", T0)).isInstanceOf(ConflictException.class);
    }

    @Test
    void aReassignedJobCanFail() {
        Job job = runningJob();
        job.reassign();

        job.fail("no compatible machine left", T0.plusSeconds(90));

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getFailureReason()).isEqualTo("no compatible machine left");
    }

    @Test
    void aVeryLongFailureReasonIsTruncated() {
        Job job = runningJob();

        job.fail("x".repeat(5_000), T0);

        assertThat(job.getFailureReason()).hasSize(Job.MAX_FAILURE_REASON_LENGTH);
    }

    @Test
    void cancelIsIdempotentButAFinishedJobCannotBeCancelled() {
        Job running = runningJob();
        assertThat(running.cancel(T0.plusSeconds(30))).isTrue();
        assertThat(running.cancel(T0.plusSeconds(40))).isFalse();
        assertThat(running.getFinishedAt()).isEqualTo(T0.plusSeconds(30));
        assertThat(running.getMachine()).as("kept to stop the agent").isEqualTo(PC_12);

        Job done = runningJob();
        done.complete(0.9, T0);
        assertThatThrownBy(() -> done.cancel(T0)).isInstanceOf(ConflictException.class);
    }

    @Test
    void copyIsIndependent() {
        Job job = pendingJob();
        Job copy = job.copy();

        job.start(PC_12, T0);

        assertThat(copy.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(copy.getMachine()).isNull();
    }

    @Test
    void submissionIsValidated() {
        UserRef owner = new UserRef("u-1", "Haroun", "MEMBER");
        JobParams params = new JobParams(10, 0.01, 64);

        assertThatThrownBy(() -> new JobSubmission(owner, " ", JobType.MNIST_TRAIN, params, JobRequirements.CPU))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new JobSubmission(owner, "x".repeat(256), JobType.MNIST_TRAIN, params,
                JobRequirements.CPU))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new JobParams(0, 0.01, 64)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new JobParams(10, Double.NaN, 64)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new JobProgress(1, 0.5, 101)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new JobCheckpoint(1, "key", "NOT-A-SHA")).isInstanceOf(ValidationException.class);
    }
}
