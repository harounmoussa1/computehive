/**
 * <p>Module <b>scheduling</b> — placement des jobs, réattribution, fencing et dialogue avec les agents.</p>
 *
 * <p>Couche <b>service-impl</b> : services CRUD (seuls propriétaires des repositories), services métier, intercepteurs et implémentation de la façade.</p>
 *
 * <p>Contenu : {@code AssignmentCrudService}, {@code ProcessedAgentMessageCrudService} ; {@link tn.eniso.computehive.modules.scheduling.service.impl.SchedulingModuleImpl} (façade : messages d'agent, dédoublonnage, fencing) ; {@code JobPlacement} (placement périodique, par priorité quotas puis date), {@code LaunchWatchdog} (renvoi des LaunchJob sans réponse), {@code Fencing}, {@code CheckpointVerifier} (clé, dépôt et SHA-256 d'un checkpoint avant qu'il devienne officiel), {@code AttemptLifecycle} (opérations sur plusieurs modules, sans transaction englobante) ; {@code Reassignment} (panne de machine, annulation, job perdu), {@code SchedulingSubscriptions} (abonnements à {@code machines.machine-dead} et {@code jobs.job-cancelled}), {@code Reconciler} (réparation périodique de l'état) ; {@code AttemptMetrics} (intercepteur AFTER_ADD / AFTER_UPDATE : métriques Micrometer des tentatives, ADR 0014) ; {@code SchedulingConfiguration}, {@code SchedulingProperties}.</p>
 */
package tn.eniso.computehive.modules.scheduling.service.impl;
