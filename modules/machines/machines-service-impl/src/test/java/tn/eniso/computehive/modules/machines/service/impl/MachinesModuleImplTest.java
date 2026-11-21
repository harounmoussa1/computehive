package tn.eniso.computehive.modules.machines.service.impl;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorDispatcher;
import tn.eniso.computehive.modules.machines.infra.GpuSpec;
import tn.eniso.computehive.modules.machines.infra.HeartbeatReport;
import tn.eniso.computehive.modules.machines.infra.MachineInfo;
import tn.eniso.computehive.modules.machines.infra.MachineKind;
import tn.eniso.computehive.modules.machines.infra.MachineRegistration;
import tn.eniso.computehive.modules.machines.infra.MachineRequirements;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;
import tn.eniso.computehive.modules.machines.service.impl.entity.MachineCrudService;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachinesModuleImplTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-21T10:00:00Z"));
    private final MachinesModuleImpl module = new MachinesModuleImpl(
            new MachineCrudService(new InMemoryMachineRepository(), Mockito.mock(InterceptorDispatcher.class)),
            new MachineSelector(), MachinesProperties.defaults(), clock);

    private static MachineRegistration gpu(String agentId, String name, long memoryMb) {
        return new MachineRegistration(agentId, name, "0.1.0", 8, 32_000, new GpuSpec("RTX", memoryMb, true));
    }

    private static MachineRegistration cpu(String agentId, String name, int cores) {
        return new MachineRegistration(agentId, name, "0.1.0", cores, 16_000, null);
    }

    @Test
    void registersANewMachineAsOnline() {
        MachineInfo machine = module.register(gpu("agent-1", "pc-12", 12_288));

        assertThat(machine.id()).isNotBlank();
        assertThat(machine.status()).isEqualTo(MachineStatus.ONLINE);
        assertThat(machine.kind()).isEqualTo(MachineKind.GPU);
        assertThat(module.findAll()).hasSize(1);
    }

    @Test
    void aKnownAgentKeepsItsMachineIdAndADeadMachineComesBack() {
        MachineInfo first = module.register(gpu("agent-1", "pc-12", 12_288));
        module.markDead(first.id());

        MachineInfo again = module.register(gpu("agent-1", "pc-12-renamed", 12_288));

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.name()).isEqualTo("pc-12-renamed");
        assertThat(again.status()).isEqualTo(MachineStatus.ONLINE);
        assertThat(module.findAll()).hasSize(1);
    }

    @Test
    void heartbeatExtendsTheLeaseAndUpdatesMetrics() {
        MachineInfo machine = module.register(gpu("agent-1", "pc-12", 12_288));
        clock.advance(Duration.ofSeconds(10));

        MachineInfo updated = module.heartbeat(machine.id(), new HeartbeatReport(8_000L, 40));

        assertThat(updated.lastHeartbeat()).isEqualTo(clock.instant());
        assertThat(updated.gpuMemoryFreeMb()).isEqualTo(8_000);
        assertThat(updated.gpuUtilizationPercent()).isEqualTo(40);
    }

    @Test
    void heartbeatFromAnUnknownMachineIsRejected() {
        assertThatThrownBy(() -> module.heartbeat("ghost", HeartbeatReport.EMPTY))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void reserveAndRelease() {
        MachineInfo machine = module.register(gpu("agent-1", "pc-12", 12_288));

        assertThat(module.reserve(machine.id()).status()).isEqualTo(MachineStatus.BUSY);
        assertThatThrownBy(() -> module.reserve(machine.id())).isInstanceOf(ConflictException.class);
        assertThat(module.release(machine.id()).status()).isEqualTo(MachineStatus.ONLINE);
    }

    @Test
    void gpuJobsGetOnlineCudaMachinesWithEnoughMemoryMostFreeFirst() {
        module.register(gpu("a1", "small", 4_096));
        module.register(gpu("a2", "big", 24_576));
        module.register(gpu("a3", "medium", 12_288));
        MachineInfo busy = module.register(gpu("a4", "busy-big", 48_000));
        module.reserve(busy.id());
        module.register(cpu("a5", "cpu-only", 16));

        assertThat(module.findAvailable(MachineRequirements.gpu(8_000)))
                .extracting(MachineInfo::name)
                .containsExactly("big", "medium");
    }

    @Test
    void cpuJobsPreferCpuWorkersToKeepGpusFree() {
        module.register(gpu("a1", "gpu-pc", 12_288));
        module.register(cpu("a2", "cpu-4", 4));
        module.register(cpu("a3", "cpu-16", 16));

        assertThat(module.findAvailable(MachineRequirements.ANY))
                .extracting(MachineInfo::name)
                .containsExactly("cpu-16", "cpu-4", "gpu-pc");
    }

    @Test
    void markDeadIsIdempotent() {
        MachineInfo machine = module.register(cpu("a1", "pc", 4));

        module.markDead(machine.id());

        assertThat(module.markDead(machine.id()).status()).isEqualTo(MachineStatus.DEAD);
    }

    @Test
    void leasePolicyComesFromTheConfiguration() {
        assertThat(module.leasePolicy().heartbeatInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(module.leasePolicy().leaseTtl()).isEqualTo(Duration.ofSeconds(30));
    }
}
