/**
 * <p>Module <b>jobs</b> — cycle de vie des jobs soumis par les utilisateurs.</p>
 *
 * <p>Couche <b>service-impl</b> : services CRUD (seuls propriétaires des repositories), services métier, intercepteurs et implémentation de la façade.</p>
 *
 * <p>Contenu : {@code JobCrudService} (seul utilisateur de {@code JobRepository}, appelle les intercepteurs), {@link tn.eniso.computehive.modules.jobs.service.impl.JobsModuleImpl} (façade), {@code JobCancelledPublisher} (intercepteur AFTER_UPDATE : publie {@code jobs.job-cancelled}), {@code JobFinishedNotifier} (intercepteur AFTER_UPDATE : prévient le propriétaire d'un job DONE ou FAILED via {@code notifications-api}), {@code JobMetrics} (intercepteur AFTER_ADD / AFTER_UPDATE : métriques Micrometer des jobs, ADR 0014).</p>
 */
package tn.eniso.computehive.modules.jobs.service.impl;
