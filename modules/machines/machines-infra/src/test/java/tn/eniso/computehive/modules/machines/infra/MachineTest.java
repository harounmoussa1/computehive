package tn.eniso.computehive.modules.machines.infra;

import org.junit.jupiter.api.Test;
import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.exception.ValidationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineTest {

    private static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");

    private static MachineRegistration gpuRegistration() {
        return new MachineRegistration("agent-1", "labo-pc-12", "0.1.0", 8, 32_000,
                new GpuSpec("RTX 3060", 12_288, true));
    }

    private static MachineRegistration cpuRegistration() {
        return new MachineRegistration("agent-2", "labo-pc-03", "0.1.0", 4, 16_000, null);
    }

    @Test
    void registrationCreatesAnOnlineMachine() {
        Machine machine = Machine.register("m-1", gpuRegistration(), T0);

        assertThat(machine.getStatus()).isEqualTo(MachineStatus.ONLINE);
        assertThat(machine.kind()).isEqualTo(MachineKind.GPU);
        assertThat(machine.getGpuMemoryFreeMb()).isEqualTo(12_288);
        assertThat(machine.isCuda()).isTrue();
        assertThat(machine.getRegisteredAt()).isEqualTo(T0);
        assertThat(machine.getLastHeartbeat()).isEqualTo(T0);
    }

    @Test
    void machineWithoutGpuIsACpuWorker() {
        Machine machine = Machine.register("m-2", cpuRegistration(), T0);

        assertThat(machine.kind()).isEqualTo(MachineKind.CPU);
        assertThat(machine.isCuda()).isFalse();
        assertThat(machine.toInfo().toRef().type()).isEqualTo("CPU");
    }

    @Test
    void reserveAndReleaseMoveBetweenOnlineAndBusy() {
        Machine machine = Machine.register("m-1", gpuRegistration(), T0);

        machine.reserve();
        assertThat(machine.getStatus()).isEqualTo(MachineStatus.BUSY);

        machine.release();
        assertThat(machine.getStatus()).isEqualTo(MachineStatus.ONLINE);
    }

    @Test
    void onlyAnOnlineMachineCanBeReserved() {
        Machine machine = Machine.register("m-1", gpuRegistration(), T0);
        machine.reserve();

        assertThatThrownBy(machine::reserve).isInstanceOf(ConflictException.class);

        machine.markDead();
        assertThatThrownBy(machine::reserve).isInstanceOf(ConflictException.class);
    }

    @Test
    void markDeadIsIdempotent() {
        Machine machine = Machine.register("m-1", gpuRegistration(), T0);

        assertThat(machine.markDead()).isTrue();
        assertThat(machine.markDead()).isFalse();
        assertThat(machine.getStatus()).isEqualTo(MachineStatus.DEAD);
    }

    @Test
    void heartbeatUpdatesGpuMetricsAndRevivesADeadMachine() {
        Machine machine = Machine.register("m-1", gpuRegistration(), T0);
        machine.markDead();

        machine.recordHeartbeat(new HeartbeatReport(4_000L, 85), T0.plusSeconds(40));

        assertThat(machine.getStatus()).isEqualTo(MachineStatus.ONLINE);
        assertThat(machine.getGpuMemoryFreeMb()).isEqualTo(4_000);
        assertThat(machine.getGpuUtilizationPercent()).isEqualTo(85);
        assertThat(machine.getLastHeartbeat()).isEqualTo(T0.plusSeconds(40));
    }

    @Test
    void heartbeatCannotReportMoreFreeMemoryThanTheGpuHas() {
        Machine machine = Machine.register("m-1", gpuRegistration(), T0);

        assertThatThrownBy(() -> machine.recordHeartbeat(new HeartbeatReport(20_000L, 10), T0))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void heartbeatGpuMetricsAreIgnoredOnACpuWorker() {
        Machine machine = Machine.register("m-2", cpuRegistration(), T0);

        machine.recordHeartbeat(new HeartbeatReport(1_000L, 50), T0.plusSeconds(10));

        assertThat(machine.getGpuMemoryFreeMb()).isNull();
        assertThat(machine.getLastHeartbeat()).isEqualTo(T0.plusSeconds(10));
    }

    @Test
    void reRegistrationRevivesADeadMachineButKeepsABusyOneBusy() {
        Machine dead = Machine.register("m-1", gpuRegistration(), T0);
        dead.markDead();
        dead.applyRegistration(gpuRegistration(), T0.plusSeconds(60));
        assertThat(dead.getStatus()).isEqualTo(MachineStatus.ONLINE);

        Machine busy = Machine.register("m-1", gpuRegistration(), T0);
        busy.reserve();
        busy.applyRegistration(gpuRegistration(), T0.plusSeconds(5));
        assertThat(busy.getStatus()).isEqualTo(MachineStatus.BUSY);
    }

    @Test
    void agentIdCannotChange() {
        Machine machine = Machine.register("m-1", gpuRegistration(), T0);

        assertThatThrownBy(() -> machine.applyRegistration(cpuRegistration(), T0))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void leaseExpiresOnlyForLivingMachinesSilentSinceTheCutoff() {
        Machine machine = Machine.register("m-1", gpuRegistration(), T0);

        assertThat(machine.isLeaseExpired(T0.minusSeconds(1))).isFalse();
        assertThat(machine.isLeaseExpired(T0.plusSeconds(1))).isTrue();

        machine.markDead();
        assertThat(machine.isLeaseExpired(T0.plusSeconds(1))).isFalse();
    }

    @Test
    void copyIsIndependent() {
        Machine machine = Machine.register("m-1", gpuRegistration(), T0);
        Machine copy = machine.copy();

        machine.reserve();

        assertThat(copy.getStatus()).isEqualTo(MachineStatus.ONLINE);
        assertThat(copy.getId()).isEqualTo("m-1");
    }
}
