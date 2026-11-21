/**
 * <p>Module <b>scheduling</b> — placement des jobs, réattribution, fencing et dialogue avec les agents.</p>
 *
 * <p>Couche <b>service-impl</b> : services CRUD (seuls propriétaires des repositories), services métier, intercepteurs et implémentation de la façade.</p>
 *
 * <p>Contenu : {@code AssignmentCrudService}, {@code ProcessedAgentMessageCrudService} ; {@link tn.eniso.computehive.modules.scheduling.service.impl.SchedulingModuleImpl} (façade : messages d'agent, dédoublonnage, fencing) ; {@code JobPlacement} (placement périodique), {@code LaunchWatchdog} (renvoi des LaunchJob sans réponse), {@code Fencing}, {@code AttemptLifecycle} (opérations sur plusieurs modules, sans transaction englobante) ; {@code SchedulingConfiguration}, {@code SchedulingProperties}. À venir (étape 6) : priorité par quotas, réattribution sur {@code MachineDead}, arrêt sur annulation.</p>
 */
package tn.eniso.computehive.modules.scheduling.service.impl;
