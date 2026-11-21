package tn.eniso.computehive.modules.machines.infra;

/**
 * État d'une machine, décidé uniquement par le serveur.
 *
 * <ul>
 *   <li>{@code ONLINE} : connectée, heartbeats à jour, libre pour un job ;</li>
 *   <li>{@code BUSY} : connectée et occupée par un job ;</li>
 *   <li>{@code DEAD} : aucun heartbeat depuis la durée du bail ; ses jobs sont réattribués.</li>
 * </ul>
 */
public enum MachineStatus {
    ONLINE,
    BUSY,
    DEAD
}
