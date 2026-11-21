/**
 * <p>Module <b>scheduling</b> — placement des jobs, réattribution, fencing et dialogue avec les agents.</p>
 *
 * <p>Couche <b>service-impl</b> : services CRUD (seuls propriétaires des repositories), services métier, intercepteurs et implémentation de la façade.</p>
 *
 * <p>Contenu prévu : placement (priorité quotas puis date), réattribution sur {@code MachineDead}, fencing par attemptId, {@code AgentSessionRegistry}.</p>
 */
package tn.eniso.computehive.modules.scheduling.service.impl;
