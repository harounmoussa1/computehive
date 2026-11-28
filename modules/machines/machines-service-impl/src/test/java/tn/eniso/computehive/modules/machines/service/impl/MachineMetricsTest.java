package tn.eniso.computehive.modules.machines.service.impl;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorDispatcher;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorEvent;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.modules.machines.infra.GpuSpec;
import tn.eniso.computehive.modules.machines.infra.HeartbeatReport;
import tn.eniso.computehive.modules.machines.infra.Machine;
import tn.eniso.computehive.modules.machines.infra.MachineRegistration;
import tn.eniso.computehive.modules.machines.service.impl.entity.MachineCrudService;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MachineMetricsTest {

    private final MeterRegistry meters = new SimpleMeterRegistry();
    private MachineMetrics metrics;

    /** Distributeur minimal : transmet les AFTER_* de machines aux métriques, comme le ferait le core. */
    private final InterceptorDispatcher dispatcher = new InterceptorDispatcher() {
        @Override
        public <T> void dispatch(LifecyclePhase phase, T entity, T previous) {
            if (metrics.phases().contains(phase) && entity instanceof Machine machine) {
                metrics.onEvent(new InterceptorEvent<>(phase, machine, (Machine) previous));
            }
        }
    };

    private final MachineCrudService crud = new MachineCrudService(new InMemoryMachineRepository(), dispatcher);
    private final MachinesModuleImpl machines = new MachinesModuleImpl(crud, new MachineSelector(),
            MachinesProperties.defaults(), new MutableClock(Instant.parse("2026-11-21T10:00:00Z")));

    MachineMetricsTest() {
        metrics = new MachineMetrics(meters, crud);
    }

    private String gpuMachine(String name) {
        return machines.register(new MachineRegistration("agent-" + name, name, "0.1.0", 8, 32_000,
                new GpuSpec("RTX 3060", 12_288, true))).id();
    }

    private double machinesWithStatus(String status) {
        return meters.get("computehive.machines").tag("status", status).gauge().value();
    }

    private double gpuMemoryFree(String machine) {
        return meters.get("computehive.machine.gpu.memory.free").tag("machine", machine).gauge().value();
    }

    @Test
    void machinesAreCountedByStatusAndEachFailureIsCountedOnce() {
        String pcA = gpuMachine("pc-a");
        String pcB = gpuMachine("pc-b");
        gpuMachine("pc-c");
        machines.reserve(pcB);
        machines.markDead(pcA);
        machines.markDead(pcA);

        assertThat(machinesWithStatus("online")).isEqualTo(1);
        assertThat(machinesWithStatus("busy")).isEqualTo(1);
        assertThat(machinesWithStatus("dead")).isEqualTo(1);
        assertThat(meters.get("computehive.machines.failures").counter().count()).isEqualTo(1);
    }

    @Test
    void theGpuStateOfEachMachineFollowsItsHeartbeats() {
        String pcA = gpuMachine("pc-a");
        assertThat(gpuMemoryFree("pc-a")).isEqualTo(12_288);

        machines.heartbeat(pcA, new HeartbeatReport(9_800L, 35));

        assertThat(gpuMemoryFree("pc-a")).isEqualTo(9_800);
        assertThat(meters.get("computehive.machine.gpu.utilization").tag("machine", "pc-a").gauge().value())
                .isEqualTo(35);
    }

    @Test
    void aDeadMachineHasNoGpuValueRatherThanAStaleOne() {
        String pcA = gpuMachine("pc-a");
        machines.heartbeat(pcA, new HeartbeatReport(9_800L, 35));

        machines.markDead(pcA);

        assertThat(gpuMemoryFree("pc-a")).isNaN();
    }
}
