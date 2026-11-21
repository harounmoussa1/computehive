/**
 * <p>Module <b>quotas</b> — consommation gpu par utilisateur et priorité de placement.</p>
 *
 * <p>Couche <b>service-api</b> : façade du module, son unique contrat public. Les autres modules et la couche WS ne voient que cette interface.</p>
 *
 * <p>Contenu prévu : façade {@code QuotasModule} ({@code priorityOf(userId)}).</p>
 */
package tn.eniso.computehive.modules.quotas.service.api;
