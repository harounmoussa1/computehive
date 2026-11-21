/**
 * <p>Module <b>machines</b> — inventaire des pc workers, heartbeats et détection de panne.</p>
 *
 * <p>Couche <b>service-impl</b> : services CRUD (seuls propriétaires des repositories), services métier, intercepteurs et implémentation de la façade.</p>
 *
 * <p>Contenu : {@code MachineCrudService}, {@code MachinesModuleImpl}, {@code MachineSelector}, {@code MachineFailureDetector} (toutes les 10 s ; pas de heartbeat depuis 30 s → DEAD), {@code MachinesProperties}, {@code MachineDeadPublisher} (intercepteur AFTER_UPDATE : publie {@code machines.machine-dead} sur {@code messaging-broker-api}).</p>
 */
package tn.eniso.computehive.modules.machines.service.impl;
