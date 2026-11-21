package tn.eniso.computehive.modules.machines.service.impl;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import tn.eniso.computehive.modules.machines.dal.api.MachineRepository;
import tn.eniso.computehive.modules.machines.infra.Machine;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Faux repository pour les tests unitaires : même contrat que l'implémentation JPA
 * (copies indépendantes, version incrémentée, verrouillage optimiste, agentId unique).
 */
class InMemoryMachineRepository implements MachineRepository {

    private final Map<String, Machine> rows = new LinkedHashMap<>();

    @Override
    public Machine save(Machine machine) {
        Machine stored = rows.get(machine.getId());
        if (stored != null && !Objects.equals(stored.getVersion(), machine.getVersion())) {
            throw new OptimisticLockingFailureException("stale version of machine " + machine.getId());
        }
        boolean agentTaken = rows.values().stream()
                .anyMatch(m -> m.getAgentId().equals(machine.getAgentId()) && !m.getId().equals(machine.getId()));
        if (agentTaken) {
            throw new DataIntegrityViolationException("duplicate agent id " + machine.getAgentId());
        }
        Machine copy = machine.copy();
        copy.setVersion(stored == null ? 0L : stored.getVersion() + 1);
        rows.put(copy.getId(), copy);
        return copy.copy();
    }

    @Override
    public Optional<Machine> findById(String id) {
        return Optional.ofNullable(rows.get(id)).map(Machine::copy);
    }

    @Override
    public Optional<Machine> findByAgentId(String agentId) {
        return rows.values().stream().filter(m -> m.getAgentId().equals(agentId)).findFirst().map(Machine::copy);
    }

    @Override
    public List<Machine> findAll() {
        return rows.values().stream().sorted(Comparator.comparing(Machine::getName)).map(Machine::copy).toList();
    }

    @Override
    public List<Machine> findByStatus(MachineStatus status) {
        return findAll().stream().filter(m -> m.getStatus() == status).toList();
    }

    @Override
    public List<Machine> findLeaseExpired(Instant cutoff) {
        return findAll().stream().filter(m -> m.isLeaseExpired(cutoff)).toList();
    }
}
