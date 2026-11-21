package tn.eniso.computehive.modules.machines.service.impl.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorDispatcher;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.modules.machines.dal.api.MachineRepository;
import tn.eniso.computehive.modules.machines.infra.Machine;
import tn.eniso.computehive.modules.machines.infra.MachineRegistration;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;

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
class MachineCrudServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");

    @Mock
    private MachineRepository repository;

    @Mock
    private InterceptorDispatcher interceptors;

    private Machine machine() {
        return Machine.register("m-1", new MachineRegistration("agent-1", "pc", "0.1.0", 4, 8_000, null), T0);
    }

    @Test
    void createAnnouncesTheAdditionAroundTheSave() {
        Machine machine = machine();
        when(repository.save(machine)).thenReturn(machine);

        new MachineCrudService(repository, interceptors).create(machine);

        InOrder order = inOrder(interceptors, repository);
        order.verify(interceptors).dispatch(eq(LifecyclePhase.BEFORE_ADD), eq(machine), isNull());
        order.verify(repository).save(machine);
        order.verify(interceptors).dispatch(eq(LifecyclePhase.AFTER_ADD), eq(machine), isNull());
    }

    @Test
    void updatePassesThePreviousStateToTheInterceptors() {
        when(repository.findById("m-1")).thenReturn(Optional.of(machine()));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        Machine updated = new MachineCrudService(repository, interceptors).update("m-1", m -> {
            m.reserve();
            return true;
        });

        ArgumentCaptor<Machine> previous = ArgumentCaptor.forClass(Machine.class);
        verify(interceptors).dispatch(eq(LifecyclePhase.AFTER_UPDATE), eq(updated), previous.capture());
        assertThat(previous.getValue().getStatus()).isEqualTo(MachineStatus.ONLINE);
        assertThat(updated.getStatus()).isEqualTo(MachineStatus.BUSY);
    }

    @Test
    void updateThatChangesNothingWritesNothing() {
        when(repository.findById("m-1")).thenReturn(Optional.of(machine()));

        new MachineCrudService(repository, interceptors).update("m-1", m -> false);

        verify(repository, never()).save(any());
        verifyNoInteractions(interceptors);
    }

    @Test
    void updateOfAnUnknownMachineFails() {
        when(repository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new MachineCrudService(repository, interceptors).update("ghost", m -> true))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ghost");
    }
}
