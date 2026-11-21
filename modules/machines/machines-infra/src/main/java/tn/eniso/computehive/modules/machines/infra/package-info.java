/**
 * <p>Module <b>machines</b> — inventaire des pc workers, heartbeats et détection de panne.</p>
 *
 * <p>Couche <b>infra</b> : entités du domaine, DTO et énumérations. Base de la pyramide, elle ne dépend que de {@code core-infra}.</p>
 *
 * <p>Contenu prévu : {@code Machine} (name, GPU, mémoire totale et libre, CUDA, status, lastHeartbeat), {@code MachineStatus} (ONLINE, BUSY, DEAD).</p>
 */
package tn.eniso.computehive.modules.machines.infra;
