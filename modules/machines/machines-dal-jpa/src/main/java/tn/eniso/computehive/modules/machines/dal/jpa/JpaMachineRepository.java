package tn.eniso.computehive.modules.machines.dal.jpa;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import tn.eniso.computehive.modules.machines.dal.api.MachineRepository;
import tn.eniso.computehive.modules.machines.infra.Machine;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Implémentation JPA de {@link MachineRepository}. */
@Repository
class JpaMachineRepository implements MachineRepository {

    private static final Sort BY_NAME = Sort.by("name");

    private final SpringDataMachineRepository repository;

    JpaMachineRepository(SpringDataMachineRepository repository) {
        this.repository = repository;
    }

    /**
     * {@code saveAndFlush} : la version incrémentée par Hibernate n'est connue qu'après l'écriture,
     * et un conflit de version doit être signalé ici plutôt qu'au commit.
     */
    @Override
    public Machine save(Machine machine) {
        return repository.saveAndFlush(JpaMachine.from(machine)).toDomain();
    }

    @Override
    public Optional<Machine> findById(String id) {
        return repository.findById(id).map(JpaMachine::toDomain);
    }

    @Override
    public Optional<Machine> findByAgentId(String agentId) {
        return repository.findByAgentId(agentId).map(JpaMachine::toDomain);
    }

    @Override
    public List<Machine> findAll() {
        return repository.findAll(BY_NAME).stream().map(JpaMachine::toDomain).toList();
    }

    @Override
    public List<Machine> findByStatus(MachineStatus status) {
        return repository.findByStatus(status, BY_NAME).stream().map(JpaMachine::toDomain).toList();
    }

    @Override
    public List<Machine> findLeaseExpired(Instant cutoff) {
        return repository.findByStatusNotAndLastHeartbeatBefore(MachineStatus.DEAD, cutoff).stream()
                .map(JpaMachine::toDomain)
                .toList();
    }
}
