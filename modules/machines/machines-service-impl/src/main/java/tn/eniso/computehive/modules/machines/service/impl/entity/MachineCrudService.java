package tn.eniso.computehive.modules.machines.service.impl.entity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorDispatcher;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.modules.machines.dal.api.MachineRepository;
import tn.eniso.computehive.modules.machines.infra.Machine;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Seul propriétaire de {@link MachineRepository} dans le module. Toute écriture d'une machine
 * passe par ici, et chacune est annoncée aux intercepteurs ({@code BEFORE_*} / {@code AFTER_*}).
 */
@Service
public class MachineCrudService {

    /** Modification d'une machine ; renvoie {@code false} si elle n'a rien changé. */
    @FunctionalInterface
    public interface MachineChange {
        boolean apply(Machine machine);
    }

    private final MachineRepository repository;
    private final InterceptorDispatcher interceptors;

    public MachineCrudService(MachineRepository repository, InterceptorDispatcher interceptors) {
        this.repository = repository;
        this.interceptors = interceptors;
    }

    @Transactional
    public Machine create(Machine machine) {
        interceptors.dispatch(LifecyclePhase.BEFORE_ADD, machine, null);
        Machine saved = repository.save(machine);
        interceptors.dispatch(LifecyclePhase.AFTER_ADD, saved, null);
        return saved;
    }

    /**
     * Charge la machine, applique {@code change} puis l'enregistre. Si {@code change} ne modifie
     * rien, rien n'est écrit et aucun intercepteur n'est appelé.
     *
     * @throws NotFoundException si la machine n'existe pas
     * @throws org.springframework.dao.OptimisticLockingFailureException si elle a été modifiée entre-temps
     */
    @Transactional
    public Machine update(String id, MachineChange change) {
        Machine machine = repository.findById(id).orElseThrow(() -> new NotFoundException("Machine", id));
        Machine previous = machine.copy();
        if (!change.apply(machine)) {
            return machine;
        }
        interceptors.dispatch(LifecyclePhase.BEFORE_UPDATE, machine, previous);
        Machine saved = repository.save(machine);
        interceptors.dispatch(LifecyclePhase.AFTER_UPDATE, saved, previous);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Machine> findById(String id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Machine> findByAgentId(String agentId) {
        return repository.findByAgentId(agentId);
    }

    @Transactional(readOnly = true)
    public List<Machine> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Machine> findByStatus(MachineStatus status) {
        return repository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<Machine> findLeaseExpired(Instant cutoff) {
        return repository.findLeaseExpired(cutoff);
    }
}
