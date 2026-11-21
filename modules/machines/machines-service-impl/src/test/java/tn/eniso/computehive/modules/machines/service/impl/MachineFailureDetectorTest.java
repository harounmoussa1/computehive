package tn.eniso.computehive.modules.machines.service.impl;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.OptimisticLockingFailureException;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorDispatcher;
import tn.eniso.computehive.modules.machines.infra.HeartbeatReport;
import tn.eniso.computehive.modules.machines.infra.Machine;
import tn.eniso.computehive.modules.machines.infra.MachineInfo;
import tn.eniso.computehive.modules.machines.infra.MachineRegistration;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;
import tn.eniso.computehive.modules.machines.service.impl.entity.MachineCrudService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class MachineFailureDetectorTest {

    private static final Instant START = Instant.parse("2026-11-21T10:00:00Z");

    private final MutableClock clock = new MutableClock(START);
    private final MachineCrudService crud =
            new MachineCrudService(new InMemoryMachineRepository(), Mockito.mock(InterceptorDispatcher.class));
    private final MachinesModuleImpl module =
            new MachinesModuleImpl(crud, new MachineSelector(), MachinesProperties.defaults(), clock);
    private final MachineFailureDetector detector =
            new MachineFailureDetector(crud, MachinesProperties.defaults(), clock);

    private MachineInfo register(String agentId) {
        return module.register(new MachineRegistration(agentId, agentId, "0.1.0", 4, 8_000, null));
    }

    @Test
    void killsNothingDuringTheStartupGracePeriod() {
        MachineInfo machine = register("a1");
        clock.advance(Duration.ofSeconds(29));

        assertThat(detector.detect()).isZero();
        assertThat(module.findById(machine.id()).orElseThrow().status()).isEqualTo(MachineStatus.ONLINE);
    }

    @Test
    void killsOnlyMachinesSilentForLongerThanTheLease() {
        clock.advance(Duration.ofSeconds(60));
        MachineInfo silent = register("silent");
        MachineInfo alive = register("alive");

        clock.advance(Duration.ofSeconds(25));
        module.heartbeat(alive.id(), HeartbeatReport.EMPTY);
        clock.advance(Duration.ofSeconds(10));

        assertThat(detector.detect()).isEqualTo(1);
        assertThat(module.findById(silent.id()).orElseThrow().status()).isEqualTo(MachineStatus.DEAD);
        assertThat(module.findById(alive.id()).orElseThrow().status()).isEqualTo(MachineStatus.ONLINE);
    }

    @Test
    void aBusyMachineThatGoesSilentDiesToo() {
        clock.advance(Duration.ofSeconds(60));
        MachineInfo busy = register("busy");
        module.reserve(busy.id());
        clock.advance(Duration.ofSeconds(31));

        assertThat(detector.detect()).isEqualTo(1);
        assertThat(module.findById(busy.id()).orElseThrow().status()).isEqualTo(MachineStatus.DEAD);
    }

    @Test
    void aDeadMachineIsNotKilledTwice() {
        clock.advance(Duration.ofSeconds(60));
        register("silent");
        clock.advance(Duration.ofSeconds(31));

        assertThat(detector.detect()).isEqualTo(1);
        assertThat(detector.detect()).isZero();
    }

    @Test
    void aConcurrentHeartbeatWinsOverTheDetection() {
        MachineCrudService racingCrud = Mockito.mock(MachineCrudService.class);
        Machine candidate = Machine.register("m-1",
                new MachineRegistration("a1", "pc", "0.1.0", 4, 8_000, null), START);
        when(racingCrud.findLeaseExpired(any())).thenReturn(List.of(candidate));
        when(racingCrud.update(eq("m-1"), any())).thenThrow(new OptimisticLockingFailureException("heartbeat won"));
        clock.advance(Duration.ofMinutes(5));

        int deaths = new MachineFailureDetector(racingCrud, MachinesProperties.defaults(), clock).detect();

        assertThat(deaths).isZero();
    }
}
