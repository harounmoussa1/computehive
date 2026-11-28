package tn.eniso.computehive.modules.scheduling.service.impl;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import tn.eniso.computehive.core.infra.ref.JobRef;
import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorDispatcher;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorEvent;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.modules.scheduling.infra.Assignment;
import tn.eniso.computehive.modules.scheduling.infra.ResumePoint;
import tn.eniso.computehive.modules.scheduling.service.impl.entity.AssignmentCrudService;
import tn.eniso.computehive.modules.scheduling.service.impl.support.InMemoryAssignmentRepository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AttemptMetricsTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    private final MeterRegistry meters = new SimpleMeterRegistry();
    private AttemptMetrics metrics;

    /** Distributeur minimal : transmet les AFTER_* de tentatives aux métriques, comme le ferait le core. */
    private final InterceptorDispatcher dispatcher = new InterceptorDispatcher() {
        @Override
        public <T> void dispatch(LifecyclePhase phase, T entity, T previous) {
            if (metrics.phases().contains(phase) && entity instanceof Assignment attempt) {
                metrics.onEvent(new InterceptorEvent<>(phase, attempt, (Assignment) previous));
            }
        }
    };

    private final AssignmentCrudService assignments =
            new AssignmentCrudService(new InMemoryAssignmentRepository(), dispatcher);

    AttemptMetricsTest() {
        metrics = new AttemptMetrics(meters, assignments);
    }

    /** Tentative lancée pour le job {@code n} sur la machine {@code n}. */
    private String launched(int n, ResumePoint resumeFrom) {
        return assignments.create(Assignment.launch("a-" + n, new JobRef("j-" + n, "mnist", "MNIST_TRAIN"),
                new MachineRef("m-" + n, "pc-" + n, "GPU"), resumeFrom.fromScratch() ? 1 : 2, resumeFrom,
                "cmd-" + n, NOW)).getId();
    }

    private double count(String name, String tag, String value) {
        return meters.get(name).tag(tag, value).counter().count();
    }

    @Test
    void aResumeFromACheckpointIsToldApartFromAStartFromScratch() {
        launched(1, ResumePoint.FROM_SCRATCH);
        launched(2, new ResumePoint(6, "j-2/attempts/1/epochs/6", "a".repeat(64)));

        assertThat(count("computehive.attempts.started", "start", "scratch")).isEqualTo(1);
        assertThat(count("computehive.attempts.started", "start", "checkpoint")).isEqualTo(1);
        assertThat(meters.get("computehive.attempts.active").gauge().value()).isEqualTo(2);
    }

    @Test
    void eachAttemptIsCountedOnceWhenItEndsWithItsOutcome() {
        String completed = launched(1, ResumePoint.FROM_SCRATCH);
        String lost = launched(2, ResumePoint.FROM_SCRATCH);
        launched(3, ResumePoint.FROM_SCRATCH);

        assignments.update(completed, a -> a.confirmRunning(NOW));
        assignments.update(completed, a -> {
            a.complete(NOW);
            return true;
        });
        assignments.update(lost, a -> {
            a.lose("machine pc-2 is dead", NOW);
            return true;
        });

        assertThat(count("computehive.attempts.ended", "outcome", "completed")).isEqualTo(1);
        assertThat(count("computehive.attempts.ended", "outcome", "lost")).isEqualTo(1);
        assertThat(count("computehive.attempts.ended", "outcome", "failed")).isZero();
        assertThat(meters.get("computehive.attempts.active").gauge().value()).isEqualTo(1);
    }
}
