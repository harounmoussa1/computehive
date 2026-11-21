package tn.eniso.computehive.modules.quotas.service.impl;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.OptimisticLockingFailureException;
import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorDispatcher;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorEvent;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.modules.jobs.infra.Job;
import tn.eniso.computehive.modules.jobs.infra.JobParams;
import tn.eniso.computehive.modules.jobs.infra.JobRequirements;
import tn.eniso.computehive.modules.jobs.infra.JobStatusUpdate;
import tn.eniso.computehive.modules.jobs.infra.JobSubmission;
import tn.eniso.computehive.modules.jobs.infra.JobType;
import tn.eniso.computehive.modules.quotas.infra.UsageInfo;
import tn.eniso.computehive.modules.quotas.service.impl.entity.UsageCrudService;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Le module quotas : consommation enregistrée quand un job passe à DONE, et priorité qui en découle. */
class QuotasTest {

    private static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");
    private static final UserRef HAROUN = new UserRef("u-1", "Haroun", "MEMBER");
    private static final UserRef OTHER = new UserRef("u-2", "Other", "MEMBER");
    private static final MachineRef PC_12 = new MachineRef("m-1", "labo-pc-12", "GPU");

    private final InMemoryUsageRepository repository = new InMemoryUsageRepository();
    private final UsageCrudService usages = new UsageCrudService(repository, Mockito.mock(InterceptorDispatcher.class));
    private final JobUsageInterceptor interceptor = new JobUsageInterceptor(new UsageRecorder(usages));
    private final QuotasModuleImpl quotas = new QuotasModuleImpl(usages);
    private int jobs;

    /** Un job de {@code owner}, démarré à T0, auquel on applique {@code transition} ; l'intercepteur est prévenu. */
    private void jobOf(UserRef owner, JobRequirements requirements, Consumer<Job> transition) {
        Job job = Job.submit("j-" + (++jobs), new JobSubmission(owner, "mnist", JobType.MNIST_TRAIN,
                new JobParams(10, 0.01, 64), requirements), T0);
        job.start(PC_12, T0);
        Job previous = job.copy();
        transition.accept(job);
        interceptor.onEvent(new InterceptorEvent<>(LifecyclePhase.AFTER_UPDATE, job, previous));
    }

    private void gpuJobDoneAfter(UserRef owner, Duration duration) {
        jobOf(owner, JobRequirements.gpu(4_096), job -> job.complete(0.98, T0.plus(duration)));
    }

    @Test
    void aFinishedGpuJobAddsItsDurationToItsOwner() {
        gpuJobDoneAfter(HAROUN, Duration.ofMinutes(30));

        UsageInfo usage = quotas.usageOf("u-1");
        assertThat(usage.gpuSeconds()).isEqualTo(1_800);
        assertThat(usage.cpuSeconds()).isZero();
        assertThat(usage.jobsCompleted()).isEqualTo(1);
        assertThat(usage.lastJobAt()).isEqualTo(T0.plus(Duration.ofMinutes(30)));
        assertThat(quotas.priorityOf("u-1")).isCloseTo(1 / 1.5, within(1e-9));
    }

    @Test
    void aCpuJobCountsAsCpuTime() {
        jobOf(HAROUN, JobRequirements.CPU, job -> job.complete(0.9, T0.plus(Duration.ofHours(1))));

        assertThat(quotas.usageOf("u-1").cpuSeconds()).isEqualTo(3_600);
        assertThat(quotas.usageOf("u-1").gpuSeconds()).isZero();
    }

    @Test
    void consumptionAccumulatesOverJobs() {
        gpuJobDoneAfter(HAROUN, Duration.ofMinutes(30));
        gpuJobDoneAfter(HAROUN, Duration.ofMinutes(30));

        assertThat(quotas.usageOf("u-1").gpuSeconds()).isEqualTo(3_600);
        assertThat(quotas.usageOf("u-1").jobsCompleted()).isEqualTo(2);
        assertThat(quotas.priorityOf("u-1")).isEqualTo(0.5);
    }

    @Test
    void onlyAJobReachingDoneCounts() {
        jobOf(HAROUN, JobRequirements.gpu(4_096), job -> job.fail("CUDA out of memory", T0.plusSeconds(600)));
        jobOf(HAROUN, JobRequirements.gpu(4_096), job -> job.cancel(T0.plusSeconds(600)));
        jobOf(HAROUN, JobRequirements.gpu(4_096), job -> job.apply(JobStatusUpdate.reassigned(), T0));

        assertThat(quotas.usageOf("u-1").jobsCompleted()).isZero();
        assertThat(quotas.priorityOf("u-1")).isEqualTo(1.0);
    }

    @Test
    void aUserWhoNeverFinishedAJobHasTheHighestPriority() {
        assertThat(quotas.priorityOf("newcomer")).isEqualTo(1.0);
        assertThat(quotas.usageOf("newcomer").jobsCompleted()).isZero();
    }

    @Test
    void theRankingListsTheBiggestConsumersFirst() {
        gpuJobDoneAfter(OTHER, Duration.ofMinutes(5));
        gpuJobDoneAfter(HAROUN, Duration.ofHours(2));

        assertThat(quotas.ranking()).extracting(usage -> usage.user().id()).containsExactly("u-1", "u-2");
        assertThat(quotas.priorityOf("u-1")).isLessThan(quotas.priorityOf("u-2"));
    }

    @Test
    void aConcurrentUpdateIsRetried() {
        gpuJobDoneAfter(HAROUN, Duration.ofMinutes(10));
        repository.simulateConflicts(1);

        gpuJobDoneAfter(HAROUN, Duration.ofMinutes(20));

        assertThat(quotas.usageOf("u-1").gpuSeconds()).as("both jobs counted").isEqualTo(1_800);
    }

    @Test
    void aPersistentConflictGivesUpAfterTheLastAttempt() {
        repository.simulateConflicts(UsageRecorder.MAX_ATTEMPTS);

        assertThatThrownBy(() -> gpuJobDoneAfter(HAROUN, Duration.ofMinutes(10)))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
