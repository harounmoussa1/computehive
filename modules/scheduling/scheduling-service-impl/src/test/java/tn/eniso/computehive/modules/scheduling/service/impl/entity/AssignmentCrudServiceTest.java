package tn.eniso.computehive.modules.scheduling.service.impl.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.ref.JobRef;
import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorDispatcher;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.modules.scheduling.dal.api.AssignmentRepository;
import tn.eniso.computehive.modules.scheduling.infra.Assignment;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentStatus;
import tn.eniso.computehive.modules.scheduling.infra.ResumePoint;

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
class AssignmentCrudServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");

    @Mock
    private AssignmentRepository repository;

    @Mock
    private InterceptorDispatcher interceptors;

    private Assignment assignment() {
        return Assignment.launch("a-1", new JobRef("j-7", "mnist", "MNIST_TRAIN"),
                new MachineRef("m-1", "pc", "GPU"), 1, ResumePoint.FROM_SCRATCH, "cmd-A", T0);
    }

    @Test
    void createAnnouncesTheAdditionAroundTheSave() {
        Assignment assignment = assignment();
        when(repository.save(assignment)).thenReturn(assignment);

        new AssignmentCrudService(repository, interceptors).create(assignment);

        InOrder order = inOrder(interceptors, repository);
        order.verify(interceptors).dispatch(eq(LifecyclePhase.BEFORE_ADD), eq(assignment), isNull());
        order.verify(repository).save(assignment);
        order.verify(interceptors).dispatch(eq(LifecyclePhase.AFTER_ADD), eq(assignment), isNull());
    }

    @Test
    void updatePassesThePreviousStateToTheInterceptors() {
        when(repository.findById("a-1")).thenReturn(Optional.of(assignment()));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        Assignment updated = new AssignmentCrudService(repository, interceptors).update("a-1", Assignment::confirmRunning);

        ArgumentCaptor<Assignment> previous = ArgumentCaptor.forClass(Assignment.class);
        verify(interceptors).dispatch(eq(LifecyclePhase.AFTER_UPDATE), eq(updated), previous.capture());
        assertThat(previous.getValue().getStatus()).isEqualTo(AssignmentStatus.LAUNCHING);
        assertThat(updated.getStatus()).isEqualTo(AssignmentStatus.RUNNING);
    }

    @Test
    void updateThatChangesNothingWritesNothing() {
        when(repository.findById("a-1")).thenReturn(Optional.of(assignment()));

        new AssignmentCrudService(repository, interceptors).update("a-1", a -> false);

        verify(repository, never()).save(any());
        verifyNoInteractions(interceptors);
    }

    @Test
    void updateOfAnUnknownAssignmentFails() {
        when(repository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new AssignmentCrudService(repository, interceptors).update("ghost", a -> true))
                .isInstanceOf(NotFoundException.class);
    }
}
