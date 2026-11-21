package tn.eniso.computehive.modules.machines.service.api;

import tn.eniso.computehive.modules.machines.infra.HeartbeatReport;
import tn.eniso.computehive.modules.machines.infra.LeasePolicy;
import tn.eniso.computehive.modules.machines.infra.MachineInfo;
import tn.eniso.computehive.modules.machines.infra.MachineRegistration;
import tn.eniso.computehive.modules.machines.infra.MachineRequirements;

import java.util.List;
import java.util.Optional;

/**
 * Façade du module machines : son unique contrat public.
 *
 * <p>Appelée par {@code scheduling} (inscription et heartbeats reçus des agents, choix et
 * réservation d'une machine) et par la couche ws-rest (tableau de bord).</p>
 */
public interface MachinesModule {

    /**
     * Inscrit une machine, ou met à jour sa description si son {@code agentId} est déjà connu
     * (reconnexion). Une machine {@code DEAD} redevient {@code ONLINE}.
     */
    MachineInfo register(MachineRegistration registration);

    /**
     * Enregistre un heartbeat : prolonge le bail et met à jour les mesures GPU.
     *
     * @throws tn.eniso.computehive.core.infra.exception.NotFoundException si la machine est inconnue
     */
    MachineInfo heartbeat(String machineId, HeartbeatReport report);

    /**
     * Machines {@code ONLINE} compatibles avec les besoins d'un job, de la plus adaptée à la moins
     * adaptée. Liste vide si aucune ne convient.
     */
    List<MachineInfo> findAvailable(MachineRequirements requirements);

    /**
     * Réserve une machine pour un job ({@code ONLINE → BUSY}).
     *
     * @throws tn.eniso.computehive.core.infra.exception.ConflictException si elle n'est pas {@code ONLINE}
     */
    MachineInfo reserve(String machineId);

    /** Libère une machine à la fin d'un job ({@code BUSY → ONLINE}). Sans effet sinon. */
    MachineInfo release(String machineId);

    /** Déclare une machine en panne. Sans effet si elle l'est déjà. */
    MachineInfo markDead(String machineId);

    Optional<MachineInfo> findById(String machineId);

    /** Toutes les machines, triées par nom. */
    List<MachineInfo> findAll();

    /** Intervalle de heartbeat et durée du bail, à transmettre à l'agent ({@code Registered}). */
    LeasePolicy leasePolicy();
}
