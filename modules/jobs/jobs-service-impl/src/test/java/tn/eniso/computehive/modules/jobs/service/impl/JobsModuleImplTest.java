package tn.eniso.computehive.modules.jobs.service.impl;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorDispatcher;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.modules.jobs.infra.Job;
import tn.eniso.computehive.modules.jobs.infra.JobCheckpoint;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobParams;
import tn.eniso.computehive.modules.jobs.infra.JobProgress;
import tn.eniso.computehive.modules.jobs.infra.JobQuery;
import tn.eniso.computehive.modules.jobs.infra.JobRequirements;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;
import tn.eniso.computehive.modules.jobs.infra.JobStatusUpdate;
import tn.eniso.computehive.modules.jobs.infra.JobSubmission;
import tn.eniso.computehive.modules.jobs.infra.JobType;
import tn.eniso.computehive.modules.jobs.service.impl.entity.JobCrudService;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class JobsModuleImplTest {

    private static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");
    private static final UserRef HAROUN = new UserRef("u-1", "Haroun", "MEMBER");
    private static final UserRef OTHER = new UserRef("u-2", "Other", "MEMBER");
    private static final MachineRef PC_12 = new MachineRef("m-1", "labo-pc-12", "GPU");
    private static final MachineRef PC_15 = new MachineRef("m-2", "labo-pc-15", "GPU");
    private static final String SHA = "c".repeat(64);

    private final MutableClock clock = new MutableClock(T0);
    private final InterceptorDispatcher interceptors = Mockito.mock(InterceptorDispatcher.class);
    private final JobsModuleImpl module =
            new JobsModuleImpl(new JobCrudService(new InMemoryJobRepository(), interceptors), clock);

    private JobInfo submit(UserRef owner) {
        return module.submit(new JobSubmission(owner, "mnist", JobType.MNIST_TRAIN,
                new JobParams(10, 0.01, 64), JobRequirements.gpu(4_096)));
    }

    @Test
    void submitCreatesAPendingJob() {
        JobInfo job = submit(HAROUN);

        assertThat(job.id()).isNotBlank();
        assertThat(job.status()).isEqualTo(JobStatus.PENDING);
        assertThat(job.submittedAt()).isEqualTo(T0);
        assertThat(module.findById(job.id())).contains(job);
    }

    @Test
    void aJobGoesThroughItsWholeLifeAndResumesAfterAMachineFailure() {
        String id = submit(HAROUN).id();

        module.updateStatus(id, JobStatusUpdate.running(PC_12));
        module.updateProgress(id, new JobProgress(3, 0.4, 30));
        module.recordCheckpoint(id, new JobCheckpoint(3, id + "/attempts/1/epochs/3", SHA));
        module.updateProgress(id, new JobProgress(5, 0.3, 50));

        JobInfo reassigned = module.updateStatus(id, JobStatusUpdate.reassigned());
        assertThat(reassigned.status()).isEqualTo(JobStatus.REASSIGNED);
        assertThat(reassigned.machine()).isNull();
        assertThat(reassigned.currentEpoch()).as("resumes from the checkpoint").isEqualTo(3);

        module.updateStatus(id, JobStatusUpdate.running(PC_15));
        clock.advance(Duration.ofMinutes(10));
        JobInfo done = module.updateStatus(id, JobStatusUpdate.done(0.98));

        assertThat(done.status()).isEqualTo(JobStatus.DONE);
        assertThat(done.machine()).isEqualTo(PC_15);
        assertThat(done.accuracy()).isEqualTo(0.98);
        assertThat(done.finishedAt()).isEqualTo(T0.plus(Duration.ofMinutes(10)));
    }

    @Test
    void reachingDoneIsAnnouncedToTheInterceptorsWithThePreviousState() {
        String id = submit(HAROUN).id();
        module.updateStatus(id, JobStatusUpdate.running(PC_12));

        module.updateStatus(id, JobStatusUpdate.done(0.9));

        verify(interceptors).dispatch(eq(LifecyclePhase.AFTER_UPDATE),
                argThat((Job job) -> job != null && job.getStatus() == JobStatus.DONE),
                argThat((Job previous) -> previous != null && previous.getStatus() == JobStatus.RUNNING));
    }

    @Test
    void aForbiddenTransitionIsAConflictAndChangesNothing() {
        String id = submit(HAROUN).id();

        assertThatThrownBy(() -> module.updateStatus(id, JobStatusUpdate.done(0.9)))
                .isInstanceOf(ConflictException.class);
        assertThat(module.findById(id).orElseThrow().status()).isEqualTo(JobStatus.PENDING);
    }

    @Test
    void cancelIsIdempotentAndAFinishedJobCannotBeCancelled() {
        String running = submit(HAROUN).id();
        module.updateStatus(running, JobStatusUpdate.running(PC_12));

        JobInfo cancelled = module.cancel(running);
        assertThat(cancelled.status()).isEqualTo(JobStatus.CANCELLED);
        assertThat(cancelled.machine()).as("scheduling must stop this agent").isEqualTo(PC_12);
        assertThat(module.cancel(running).status()).isEqualTo(JobStatus.CANCELLED);

        String done = submit(HAROUN).id();
        module.updateStatus(done, JobStatusUpdate.running(PC_12));
        module.updateStatus(done, JobStatusUpdate.done(0.9));
        assertThatThrownBy(() -> module.cancel(done)).isInstanceOf(ConflictException.class);
    }

    @Test
    void aReplayedCheckpointIsNotWrittenTwice() {
        String id = submit(HAROUN).id();
        module.updateStatus(id, JobStatusUpdate.running(PC_12));
        JobCheckpoint checkpoint = new JobCheckpoint(2, id + "/attempts/1/epochs/2", SHA);

        module.recordCheckpoint(id, checkpoint);
        Mockito.clearInvocations(interceptors);
        module.recordCheckpoint(id, checkpoint);

        verify(interceptors, Mockito.never()).dispatch(any(), any(), any());
    }

    @Test
    void progressOfAJobThatIsNotRunningIsRejected() {
        String id = submit(HAROUN).id();

        assertThatThrownBy(() -> module.updateProgress(id, new JobProgress(1, 0.9, 10)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void unknownJobs() {
        assertThat(module.findById("ghost")).isEmpty();
        assertThatThrownBy(() -> module.cancel("ghost")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> module.updateProgress("ghost", new JobProgress(1, 0.9, 10)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findsJobsByStatusAndOwnerOldestFirst() {
        String first = submit(HAROUN).id();
        clock.advance(Duration.ofSeconds(1));
        String second = submit(OTHER).id();
        clock.advance(Duration.ofSeconds(1));
        String third = submit(HAROUN).id();
        module.updateStatus(third, JobStatusUpdate.running(PC_12));

        assertThat(module.findByQuery(JobQuery.ALL)).extracting(JobInfo::id).containsExactly(first, second, third);
        assertThat(module.findByQuery(JobQuery.byStatus(JobStatus.PENDING)))
                .extracting(JobInfo::id).containsExactly(first, second);
        assertThat(module.findByQuery(JobQuery.byOwner("u-1"))).extracting(JobInfo::id).containsExactly(first, third);
    }
}
