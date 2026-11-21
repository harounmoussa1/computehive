/**
 * <p>Module <b>machines</b> — inventaire des pc workers, heartbeats et détection de panne.</p>
 *
 * <p>Couche <b>service-impl</b> : services CRUD (seuls propriétaires des repositories), services métier, intercepteurs et implémentation de la façade.</p>
 *
 * <p>Contenu prévu : {@code MachineCrudService}, {@code MachinesModuleImpl}, détection de panne {@code @Scheduled} (10 s ; pas de heartbeat depuis 30 s → DEAD), publication de l'événement {@code MachineDead} via {@code messaging-broker-api}.</p>
 */
package tn.eniso.computehive.modules.machines.service.impl;
