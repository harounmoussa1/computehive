/**
 * <p>Module <b>machines</b> — inventaire des pc workers, heartbeats et détection de panne.</p>
 *
 * <p>Couche <b>infra</b> : entités du domaine, DTO et énumérations. Base de la pyramide, elle ne dépend que de {@code core-infra}.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.modules.machines.infra.Machine} (entité modifiable, porte les règles de statut), {@code MachineStatus}, {@code MachineKind}, et les DTO immuables {@code MachineInfo}, {@code MachineRegistration}, {@code HeartbeatReport}, {@code MachineRequirements}, {@code LeasePolicy}.</p>
 */
package tn.eniso.computehive.modules.machines.infra;
