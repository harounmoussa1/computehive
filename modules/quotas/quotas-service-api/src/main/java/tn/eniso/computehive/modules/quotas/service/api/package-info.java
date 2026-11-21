/**
 * <p>Module <b>quotas</b> — consommation gpu par utilisateur et priorité de placement.</p>
 *
 * <p>Couche <b>service-api</b> : façade du module, son unique contrat public. Les autres modules et la couche WS ne voient que cette interface.</p>
 *
 * <p>Contenu : façade {@link tn.eniso.computehive.modules.quotas.service.api.QuotasModule} ({@code priorityOf(userId)}, {@code usageOf}, {@code ranking}).</p>
 */
package tn.eniso.computehive.modules.quotas.service.api;
