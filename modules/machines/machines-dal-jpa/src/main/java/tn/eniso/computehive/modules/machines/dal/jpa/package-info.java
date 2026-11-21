/**
 * <p>Module <b>machines</b> — inventaire des pc workers, heartbeats et détection de panne.</p>
 *
 * <p>Couche <b>dal-jpa</b> : implémentation JPA (Hibernate, PostgreSQL) des repositories de {@code dal-api}. Les tables appartiennent à ce module seul.</p>
 *
 * <p>Contenu : {@code JpaMachine} (table {@code machines.machine}, verrouillage optimiste), {@code JpaMachineRepository}, {@code MachinesJpaConfiguration} (schéma et migrations Flyway du module).</p>
 */
package tn.eniso.computehive.modules.machines.dal.jpa;
