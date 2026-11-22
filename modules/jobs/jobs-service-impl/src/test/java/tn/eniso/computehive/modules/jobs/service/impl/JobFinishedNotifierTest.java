package tn.eniso.computehive.modules.jobs.service.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorDispatcher;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorEvent;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.extensions.notifications.api.Notification;
import tn.eniso.computehive.extensions.notifications.api.Notifier;
import tn.eniso.computehive.modules.jobs.infra.Job;
import tn.eniso.computehive.modules.jobs.infra.JobParams;
import tn.eniso.computehive.modules.jobs.infra.JobRequirements;
import tn.eniso.computehive.modules.jobs.infra.JobStatusUpdate;
import tn.eniso.computehive.modules.jobs.infra.JobSubmission;
import tn.eniso.computehive.modules.jobs.infra.JobType;
import tn.eniso.computehive.modules.jobs.service.impl.entity.JobCrudService;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JobFinishedNotifierTest {

    private static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");
    private static final UserRef HAROUN = new UserRef("u-1", "Haroun", "MEMBER");
    private static final MachineRef PC_12 = new MachineRef("m-1", "labo-pc-12", "GPU");

    private final Notifier notifier = Mockito.mock(Notifier.class);
    private final JobFinishedNotifier interceptor = new JobFinishedNotifier(notifier);

    /** Distributeur minimal : transmet les AFTER_UPDATE de jobs à l'intercepteur, comme le ferait le core. */
    private final InterceptorDispatcher dispatcher = new InterceptorDispatcher() {
        @Override
        public <T> void dispatch(LifecyclePhase phase, T entity, T previous) {
            if (interceptor.phases().contains(phase) && entity instanceof Job job) {
                interceptor.onEvent(new InterceptorEvent<>(phase, job, (Job) previous));
            }
        }
    };

    private final MutableClock clock = new MutableClock(T0);
    private final JobsModuleImpl jobs = new JobsModuleImpl(new JobCrudService(new InMemoryJobRepository(), dispatcher),
            clock);

    private String runningJob() {
        String id = jobs.submit(new JobSubmission(HAROUN, "mnist-lr-0.01", JobType.MNIST_TRAIN,
                new JobParams(10, 0.01, 64), JobRequirements.gpu(4_096))).id();
        jobs.updateStatus(id, JobStatusUpdate.running(PC_12));
        return id;
    }

    private Notification sent() {
        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
        verify(notifier).notify(notification.capture());
        return notification.getValue();
    }

    @Test
    void theOwnerIsToldThatTheJobSucceeded() {
        String id = runningJob();
        clock.advance(Duration.ofMinutes(12).plusSeconds(5));

        jobs.updateStatus(id, JobStatusUpdate.done(0.9812));

        Notification notification = sent();
        assertThat(notification.recipient()).isEqualTo(HAROUN);
        assertThat(notification.category()).isEqualTo("job.done");
        assertThat(notification.subject()).isEqualTo("Votre job « mnist-lr-0.01 » est terminé");
        assertThat(notification.body()).contains("labo-pc-12", "Précision : 0.981", "Durée : 12 min 5 s");
    }

    @Test
    void theOwnerIsToldWhyTheJobFailed() {
        String id = runningJob();

        jobs.updateStatus(id, JobStatusUpdate.failed("CUDA out of memory"));

        Notification notification = sent();
        assertThat(notification.category()).isEqualTo("job.failed");
        assertThat(notification.subject()).isEqualTo("Votre job « mnist-lr-0.01 » a échoué");
        assertThat(notification.body()).contains("CUDA out of memory", "epoch 0 sur 10");
    }

    @Test
    void aCancellationOrAnIntermediateStepIsNotNotified() {
        String cancelled = runningJob();
        jobs.cancel(cancelled);
        String reassigned = runningJob();
        jobs.updateStatus(reassigned, JobStatusUpdate.reassigned());

        verify(notifier, never()).notify(any());
    }
}
