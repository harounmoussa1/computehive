package tn.eniso.computehive.modules.jobs.service.impl.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorDispatcher;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.modules.jobs.dal.api.JobRepository;
import tn.eniso.computehive.modules.jobs.infra.Job;
import tn.eniso.computehive.modules.jobs.infra.JobParams;
import tn.eniso.computehive.modules.jobs.infra.JobRequirements;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;
import tn.eniso.computehive.modules.jobs.infra.JobSubmission;
import tn.eniso.computehive.modules.jobs.infra.JobType;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobCrudServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");

    @Mock
    private JobRepository repository;

    @Mock
    private InterceptorDispatcher interceptors;

    private Job job() {
        return Job.submit("j-1", new JobSubmission(new UserRef("u-1", "Haroun", "MEMBER"), "mnist",
                JobType.MNIST_TRAIN, new JobParams(10, 0.01, 64), JobRequirements.CPU), T0);
    }

    @Test
    void createAnnouncesTheAdditionAroundTheSave() {
        Job job = job();
        when(repository.save(job)).thenReturn(job);

        new JobCrudService(repository, interceptors).create(job);

        InOrder order = inOrder(interceptors, repository);
        order.verify(interceptors).dispatch(eq(LifecyclePhase.BEFORE_ADD), eq(job), isNull());
        order.verify(repository).save(job);
        order.verify(interceptors).dispatch(eq(LifecyclePhase.AFTER_ADD), eq(job), isNull());
    }

    @Test
    void updatePassesThePreviousStateToTheInterceptors() {
        when(repository.findById("j-1")).thenReturn(Optional.of(job()));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        Job updated = new JobCrudService(repository, interceptors).update("j-1", j -> {
            j.start(new MachineRef("m-1", "pc", "CPU"), T0);
            return true;
        });

        ArgumentCaptor<Job> previous = ArgumentCaptor.forClass(Job.class);
        verify(interceptors).dispatch(eq(LifecyclePhase.AFTER_UPDATE), eq(updated), previous.capture());
        assertThat(previous.getValue().getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(updated.getStatus()).isEqualTo(JobStatus.RUNNING);
    }

    @Test
    void updateThatChangesNothingWritesNothing() {
        when(repository.findById("j-1")).thenReturn(Optional.of(job()));

        new JobCrudService(repository, interceptors).update("j-1", j -> false);

        verify(repository, never()).save(any());
        verifyNoInteractions(interceptors);
    }

    @Test
    void updateOfAnUnknownJobFails() {
        when(repository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new JobCrudService(repository, interceptors).update("ghost", j -> true))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ghost");
    }
}
