package tn.eniso.computehive.modules.scheduling.service.impl.support;

import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.modules.machines.infra.HeartbeatReport;
import tn.eniso.computehive.modules.machines.infra.LeasePolicy;
import tn.eniso.computehive.modules.machines.infra.Machine;
import tn.eniso.computehive.modules.machines.infra.MachineInfo;
import tn.eniso.computehive.modules.machines.infra.MachineRegistration;
import tn.eniso.computehive.modules.machines.infra.MachineRequirements;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;
import tn.eniso.computehive.modules.machines.service.api.MachinesModule;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Faux module machines, en mémoire, pour tester scheduling seul. Il s'appuie sur la vraie entité
 * {@link Machine} ; la sélection est simplifiée (machines compatibles triées par nom).
 */
public class FakeMachinesModule implements MachinesModule {

    private final Map<String, Machine> machines = new LinkedHashMap<>();
    private final Clock clock;

    public FakeMachinesModule(Clock clock) {
        this.clock = clock;
    }

    @Override
    public MachineInfo register(MachineRegistration registration) {
        Optional<Machine> known = machines.values().stream()
                .filter(machine -> machine.getAgentId().equals(registration.agentId()))
                .findFirst();
        Machine machine = known.orElseGet(() -> Machine.register("machine-" + registration.agentId(), registration,
                clock.instant()));
        known.ifPresent(m -> m.applyRegistration(registration, clock.instant()));
        machines.put(machine.getId(), machine);
        return machine.toInfo();
    }

    @Override
    public MachineInfo heartbeat(String machineId, HeartbeatReport report) {
        Machine machine = machine(machineId);
        machine.recordHeartbeat(report, clock.instant());
        return machine.toInfo();
    }

    @Override
    public List<MachineInfo> findAvailable(MachineRequirements requirements) {
        return machines.values().stream()
                .filter(machine -> machine.getStatus() == MachineStatus.ONLINE)
                .filter(machine -> !requirements.gpu() || (machine.hasGpu() && machine.isCuda()
                        && machine.getGpuMemoryFreeMb() >= requirements.minGpuMemoryMb()))
                .sorted(Comparator.comparing(Machine::getName))
                .map(Machine::toInfo)
                .toList();
    }

    @Override
    public MachineInfo reserve(String machineId) {
        Machine machine = machine(machineId);
        machine.reserve();
        return machine.toInfo();
    }

    @Override
    public MachineInfo release(String machineId) {
        Machine machine = machine(machineId);
        machine.release();
        return machine.toInfo();
    }

    @Override
    public MachineInfo markDead(String machineId) {
        Machine machine = machine(machineId);
        machine.markDead();
        return machine.toInfo();
    }

    @Override
    public Optional<MachineInfo> findById(String machineId) {
        return Optional.ofNullable(machines.get(machineId)).map(Machine::toInfo);
    }

    @Override
    public List<MachineInfo> findAll() {
        return machines.values().stream().map(Machine::toInfo).toList();
    }

    @Override
    public LeasePolicy leasePolicy() {
        return new LeasePolicy(Duration.ofSeconds(10), Duration.ofSeconds(30));
    }

    public MachineStatus statusOf(String machineId) {
        return machine(machineId).getStatus();
    }

    private Machine machine(String machineId) {
        Machine machine = machines.get(machineId);
        if (machine == null) {
            throw new NotFoundException("Machine", machineId);
        }
        return machine;
    }
}
