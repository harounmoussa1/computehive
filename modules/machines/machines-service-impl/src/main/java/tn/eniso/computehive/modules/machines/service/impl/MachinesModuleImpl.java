package tn.eniso.computehive.modules.machines.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.eniso.computehive.core.infra.util.Checks;
import tn.eniso.computehive.modules.machines.infra.HeartbeatReport;
import tn.eniso.computehive.modules.machines.infra.LeasePolicy;
import tn.eniso.computehive.modules.machines.infra.Machine;
import tn.eniso.computehive.modules.machines.infra.MachineInfo;
import tn.eniso.computehive.modules.machines.infra.MachineRegistration;
import tn.eniso.computehive.modules.machines.infra.MachineRequirements;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;
import tn.eniso.computehive.modules.machines.service.api.MachinesModule;
import tn.eniso.computehive.modules.machines.service.impl.entity.MachineCrudService;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implémentation locale de la façade. Chaque méthode est une transaction du module ;
 * les écritures passent par {@link MachineCrudService}, les règles de statut sont dans {@link Machine}.
 */
@Service
public class MachinesModuleImpl implements MachinesModule {

    private static final Logger log = LoggerFactory.getLogger(MachinesModuleImpl.class);

    private final MachineCrudService machines;
    private final MachineSelector selector;
    private final MachinesProperties properties;
    private final Clock clock;

    public MachinesModuleImpl(MachineCrudService machines, MachineSelector selector,
                              MachinesProperties properties, Clock clock) {
        this.machines = machines;
        this.selector = selector;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public MachineInfo register(MachineRegistration registration) {
        Checks.requireNonNull(registration, "registration");
        Optional<Machine> known = machines.findByAgentId(registration.agentId());
        Machine machine = known.isPresent()
                ? machines.update(known.get().getId(), m -> {
                    m.applyRegistration(registration, clock.instant());
                    return true;
                })
                : machines.create(Machine.register(UUID.randomUUID().toString(), registration, clock.instant()));
        log.info("Machine {} ({}) registered as {} {}", machine.getId(), machine.getName(),
                machine.kind(), known.isPresent() ? "again" : "for the first time");
        return machine.toInfo();
    }

    @Override
    @Transactional
    public MachineInfo heartbeat(String machineId, HeartbeatReport report) {
        Checks.requireNonNull(report, "report");
        Machine machine = machines.update(machineId, m -> {
            boolean wasDead = m.getStatus() == MachineStatus.DEAD;
            m.recordHeartbeat(report, clock.instant());
            if (wasDead) {
                log.info("Machine {} is alive again", machineId);
            }
            return true;
        });
        return machine.toInfo();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MachineInfo> findAvailable(MachineRequirements requirements) {
        Checks.requireNonNull(requirements, "requirements");
        return selector.select(machines.findByStatus(MachineStatus.ONLINE), requirements).stream()
                .map(Machine::toInfo)
                .toList();
    }

    @Override
    @Transactional
    public MachineInfo reserve(String machineId) {
        return machines.update(machineId, m -> {
            m.reserve();
            return true;
        }).toInfo();
    }

    @Override
    @Transactional
    public MachineInfo release(String machineId) {
        return machines.update(machineId, m -> {
            boolean busy = m.getStatus() == MachineStatus.BUSY;
            m.release();
            return busy;
        }).toInfo();
    }

    @Override
    @Transactional
    public MachineInfo markDead(String machineId) {
        return machines.update(machineId, m -> {
            boolean changed = m.markDead();
            if (changed) {
                log.warn("Machine {} marked DEAD", machineId);
            }
            return changed;
        }).toInfo();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MachineInfo> findById(String machineId) {
        return machines.findById(machineId).map(Machine::toInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MachineInfo> findAll() {
        return machines.findAll().stream().map(Machine::toInfo).toList();
    }

    @Override
    public LeasePolicy leasePolicy() {
        return new LeasePolicy(properties.heartbeatInterval(), properties.leaseTtl());
    }
}
