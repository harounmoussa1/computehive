package tn.eniso.computehive.modules.jobs.service.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorDispatcher;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorEvent;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.extensions.broker.api.MessageBroker;
import tn.eniso.computehive.modules.jobs.infra.Job;
import tn.eniso.computehive.modules.jobs.infra.JobCancelledEvent;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobParams;
import tn.eniso.computehive.modules.jobs.infra.JobRequirements;
import tn.eniso.computehive.modules.jobs.infra.JobStatusUpdate;
import tn.eniso.computehive.modules.jobs.infra.JobSubmission;
import tn.eniso.computehive.modules.jobs.infra.JobType;
import tn.eniso.computehive.modules.jobs.service.api.JobsEvents;
import tn.eniso.computehive.modules.jobs.service.impl.entity.JobCrudService;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class JobCancelledPublisherTest {

    private static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");
    private static final MachineRef PC_12 = new MachineRef("m-1", "labo-pc-12", "GPU");

    private final MessageBroker broker = Mockito.mock(MessageBroker.class);
    private final JobCancelledPublisher publisher = new JobCancelledPublisher(broker);

    /** Distributeur minimal : transmet les AFTER_UPDATE de jobs à l'intercepteur, comme le ferait le core. */
    private final InterceptorDispatcher dispatcher = new InterceptorDispatcher() {
        @Override
        public <T> void dispatch(LifecyclePhase phase, T entity, T previous) {
            if (publisher.phases().contains(phase) && entity instanceof Job job) {
                publisher.onEvent(new InterceptorEvent<>(phase, job, (Job) previous));
            }
        }
    };

    private final JobsModuleImpl module = new JobsModuleImpl(
            new JobCrudService(new InMemoryJobRepository(), dispatcher), new MutableClock(T0));

    private JobInfo submit() {
        return module.submit(new JobSubmission(new UserRef("u-1", "Haroun", "MEMBER"), "mnist", JobType.MNIST_TRAIN,
                new JobParams(10, 0.01, 64), JobRequirements.CPU));
    }

    @Test
    void cancellingARunningJobAnnouncesItWithItsMachine() {
        String id = submit().id();
        module.updateStatus(id, JobStatusUpdate.running(PC_12));

        module.cancel(id);

        ArgumentCaptor<JobCancelledEvent> event = ArgumentCaptor.forClass(JobCancelledEvent.class);
        verify(broker).publish(eq(JobsEvents.JOB_CANCELLED), eq(id), event.capture());
        assertThat(event.getValue().job().id()).isEqualTo(id);
        assertThat(event.getValue().machine()).isEqualTo(PC_12);
        assertThat(event.getValue().cancelledAt()).isEqualTo(T0);
    }

    @Test
    void aJobIsAnnouncedOnceEvenIfCancelledTwice() {
        String id = submit().id();

        module.cancel(id);
        module.cancel(id);

        verify(broker, times(1)).publish(eq(JobsEvents.JOB_CANCELLED), eq(id), any());
    }

    @Test
    void otherUpdatesAreNotAnnounced() {
        String id = submit().id();
        module.updateStatus(id, JobStatusUpdate.running(PC_12));
        module.updateStatus(id, JobStatusUpdate.done(0.9));

        verify(broker, never()).publish(any(), any(), any());
    }
}
