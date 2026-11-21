package tn.eniso.computehive.modules.machines.service.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorDispatcher;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorEvent;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.extensions.broker.api.MessageBroker;
import tn.eniso.computehive.modules.machines.infra.HeartbeatReport;
import tn.eniso.computehive.modules.machines.infra.Machine;
import tn.eniso.computehive.modules.machines.infra.MachineDeadEvent;
import tn.eniso.computehive.modules.machines.infra.MachineInfo;
import tn.eniso.computehive.modules.machines.infra.MachineRegistration;
import tn.eniso.computehive.modules.machines.service.api.MachinesEvents;
import tn.eniso.computehive.modules.machines.service.impl.entity.MachineCrudService;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MachineDeadPublisherTest {

    private static final Instant START = Instant.parse("2026-11-21T10:00:00Z");

    private final MessageBroker broker = Mockito.mock(MessageBroker.class);
    private final MachineDeadPublisher publisher = new MachineDeadPublisher(broker);

    /** Distributeur minimal : transmet les AFTER_UPDATE de machines à l'intercepteur, comme le ferait le core. */
    private final InterceptorDispatcher dispatcher = new InterceptorDispatcher() {
        @Override
        public <T> void dispatch(LifecyclePhase phase, T entity, T previous) {
            if (publisher.phases().contains(phase) && entity instanceof Machine machine) {
                publisher.onEvent(new InterceptorEvent<>(phase, machine, (Machine) previous));
            }
        }
    };

    private final MutableClock clock = new MutableClock(START);
    private final MachineCrudService crud = new MachineCrudService(new InMemoryMachineRepository(), dispatcher);
    private final MachinesModuleImpl module =
            new MachinesModuleImpl(crud, new MachineSelector(), MachinesProperties.defaults(), clock);
    private final MachineFailureDetector detector =
            new MachineFailureDetector(crud, MachinesProperties.defaults(), clock);

    private static Machine machine() {
        return Machine.register("m-1", new MachineRegistration("a1", "labo-pc-12", "0.1.0", 4, 8_000, null), START);
    }

    private MachineInfo register(String agentId) {
        return module.register(new MachineRegistration(agentId, "pc-" + agentId, "0.1.0", 4, 8_000, null));
    }

    @Test
    void publishesWhenAMachineBecomesDead() {
        Machine previous = machine();
        Machine dead = previous.copy();
        dead.markDead();

        publisher.onEvent(new InterceptorEvent<>(LifecyclePhase.AFTER_UPDATE, dead, previous));

        ArgumentCaptor<MachineDeadEvent> event = ArgumentCaptor.forClass(MachineDeadEvent.class);
        verify(broker).publish(eq(MachinesEvents.MACHINE_DEAD), eq("m-1"), event.capture());
        assertThat(event.getValue().machine().id()).isEqualTo("m-1");
        assertThat(event.getValue().machine().name()).isEqualTo("labo-pc-12");
        assertThat(event.getValue().lastHeartbeat()).isEqualTo(START);
    }

    @Test
    void ignoresUpdatesThatAreNotADeath() {
        Machine online = machine();
        Machine busy = online.copy();
        busy.reserve();
        Machine dead = online.copy();
        dead.markDead();
        Machine stillDead = dead.copy();
        stillDead.recordHeartbeat(HeartbeatReport.EMPTY, START);
        stillDead.markDead();

        publisher.onEvent(new InterceptorEvent<>(LifecyclePhase.AFTER_UPDATE, busy, online));
        publisher.onEvent(new InterceptorEvent<>(LifecyclePhase.AFTER_UPDATE, stillDead, stillDead.copy()));

        verify(broker, never()).publish(any(), any(), any());
    }

    @Test
    void theFailureDetectorAnnouncesEachDeathOnce() {
        clock.advance(Duration.ofSeconds(60));
        MachineInfo silent = register("silent");
        MachineInfo alive = register("alive");
        clock.advance(Duration.ofSeconds(25));
        module.heartbeat(alive.id(), HeartbeatReport.EMPTY);
        clock.advance(Duration.ofSeconds(10));

        detector.detect();
        detector.detect();

        verify(broker, times(1)).publish(eq(MachinesEvents.MACHINE_DEAD), eq(silent.id()), any());
        verify(broker, never()).publish(any(), eq(alive.id()), any());
    }

    @Test
    void aMachineThatComesBackAndDiesAgainIsAnnouncedAgain() {
        MachineInfo machine = register("flaky");

        module.markDead(machine.id());
        module.markDead(machine.id());
        module.heartbeat(machine.id(), HeartbeatReport.EMPTY);
        module.markDead(machine.id());

        verify(broker, times(2)).publish(eq(MachinesEvents.MACHINE_DEAD), eq(machine.id()), any());
    }
}
