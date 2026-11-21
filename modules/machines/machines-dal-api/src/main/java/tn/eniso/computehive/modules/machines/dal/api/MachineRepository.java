package tn.eniso.computehive.modules.machines.dal.api;

import tn.eniso.computehive.modules.machines.infra.Machine;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Accès aux machines. Seul {@code MachineCrudService} l'utilise.
 *
 * <p>{@link #save} applique le verrouillage optimiste : enregistrer une machine dont la
 * version est périmée lève une {@code OptimisticLockingFailureException}.</p>
 */
public interface MachineRepository {

    /** Insère ou met à jour la machine, et renvoie l'état enregistré (avec sa nouvelle version). */
    Machine save(Machine machine);

    Optional<Machine> findById(String id);

    Optional<Machine> findByAgentId(String agentId);

    /** Toutes les machines, triées par nom. */
    List<Machine> findAll();

    List<Machine> findByStatus(MachineStatus status);

    /** Machines non {@code DEAD} dont le dernier heartbeat est antérieur à {@code cutoff}. */
    List<Machine> findLeaseExpired(Instant cutoff);
}
