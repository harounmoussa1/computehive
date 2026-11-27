package tn.eniso.computehive.modules.jobs.service.impl;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
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
import tn.eniso.computehive.modules.jobs.service.impl.entity.JobCrudService;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JobMetricsTest {

    private static final UserRef HAROUN = new UserRef("u-1", "Haroun", "MEMBER");
    private static final MachineRef PC_12 = new MachineRef("m-1", "labo-pc-12", "GPU");

    private final MeterRegistry meters = new SimpleMeterRegistry();
    private JobMetrics metrics;

    /** Distributeur minimal : transmet les AFTER_* de jobs aux métriques, comme le ferait le core. */
    private final InterceptorDispatcher dispatcher = new InterceptorDispatcher() {
        @Override
        public <T> void dispatch(LifecyclePhase phase, T entity, T previous) {
            if (metrics.phases().contains(phase) && entity instanceof Job job) {
                metrics.onEvent(new InterceptorEvent<>(phase, job, (Job) previous));
            }
        }
    };

    private final JobCrudService crud = new JobCrudService(new InMemoryJobRepository(), dispatcher);
    private final JobsModuleImpl jobs = new JobsModuleImpl(crud, new MutableClock(Instant.parse("2026-11-21T10:00:00Z")));

    JobMetricsTest() {
        metrics = new JobMetrics(meters, crud);
    }

    private String submitted() {
        return jobs.submit(new JobSubmission(HAROUN, "mnist", JobType.MNIST_TRAIN, new JobParams(10, 0.01, 64),
                JobRequirements.gpu(4_096))).id();
    }

    private String running() {
        String id = submitted();
        jobs.updateStatus(id, JobStatusUpdate.running(PC_12));
        return id;
    }

    private double counter(String name, String... tags) {
        return meters.get(name).tags(tags).counter().count();
    }

    private double active(String status) {
        return meters.get("computehive.jobs.active").tag("status", status).gauge().value();
    }

    @Test
    void submissionsAndEndingsAreCountedByFinalStatus() {
        jobs.updateStatus(running(), JobStatusUpdate.done(0.98));
        jobs.updateStatus(running(), JobStatusUpdate.failed("CUDA out of memory"));
        jobs.cancel(submitted());

        assertThat(counter("computehive.jobs.submitted")).isEqualTo(3);
        assertThat(counter("computehive.jobs.finished", "status", "done")).isEqualTo(1);
        assertThat(counter("computehive.jobs.finished", "status", "failed")).isEqualTo(1);
        assertThat(counter("computehive.jobs.finished", "status", "cancelled")).isEqualTo(1);
    }

    @Test
    void aLostMachineCountsAsAReassignmentAndAProgressUpdateCountsNothing() {
        String id = running();
        jobs.updateStatus(id, JobStatusUpdate.reassigned());
        jobs.updateStatus(id, JobStatusUpdate.running(PC_12));

        assertThat(counter("computehive.jobs.reassigned")).isEqualTo(1);
        assertThat(counter("computehive.jobs.finished", "status", "done")).isZero();
    }

    @Test
    void activeJobsAreReadFromTheDatabaseByStatus() {
        submitted();
        running();
        String reassigned = running();
        jobs.updateStatus(reassigned, JobStatusUpdate.reassigned());
        jobs.updateStatus(running(), JobStatusUpdate.done(0.98));

        assertThat(active("pending")).isEqualTo(1);
        assertThat(active("running")).isEqualTo(1);
        assertThat(active("reassigned")).isEqualTo(1);
    }
}
