/**
 * <p>Module <b>jobs</b> — cycle de vie des jobs soumis par les utilisateurs.</p>
 *
 * <p>Couche <b>service-impl</b> : services CRUD (seuls propriétaires des repositories), services métier, intercepteurs et implémentation de la façade.</p>
 *
 * <p>Contenu : {@code JobCrudService} (seul utilisateur de {@code JobRepository}, appelle les intercepteurs), {@link tn.eniso.computehive.modules.jobs.service.impl.JobsModuleImpl} (façade). À venir : notification de fin de job via l'extension {@code notifications-api}.</p>
 */
package tn.eniso.computehive.modules.jobs.service.impl;
