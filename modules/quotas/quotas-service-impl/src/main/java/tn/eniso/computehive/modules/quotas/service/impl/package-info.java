/**
 * <p>Module <b>quotas</b> — consommation gpu par utilisateur et priorité de placement.</p>
 *
 * <p>Couche <b>service-impl</b> : services CRUD (seuls propriétaires des repositories), services métier, intercepteurs et implémentation de la façade.</p>
 *
 * <p>Contenu prévu : {@code UsageCrudService}, intercepteur {@code AFTER_UPDATE} sur {@code Job} (passage à DONE → ajout de la durée GPU).</p>
 */
package tn.eniso.computehive.modules.quotas.service.impl;
