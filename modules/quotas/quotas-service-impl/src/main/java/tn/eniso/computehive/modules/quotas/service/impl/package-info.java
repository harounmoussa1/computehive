/**
 * <p>Module <b>quotas</b> — consommation gpu par utilisateur et priorité de placement.</p>
 *
 * <p>Couche <b>service-impl</b> : services CRUD (seuls propriétaires des repositories), services métier, intercepteurs et implémentation de la façade.</p>
 *
 * <p>Contenu : {@code UsageCrudService} (écritures en {@code REQUIRES_NEW}), {@code UsageRecorder} (ajout d'un job terminé, retenté en cas de conflit), {@link tn.eniso.computehive.modules.quotas.service.impl.JobUsageInterceptor} (intercepteur {@code AFTER_UPDATE} sur {@code Job} : passage à DONE → ajout de sa durée), {@code QuotasModuleImpl} (façade).</p>
 */
package tn.eniso.computehive.modules.quotas.service.impl;
