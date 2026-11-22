/**
 * <p>Module <b>quotas</b> — consommation gpu par utilisateur et priorité de placement.</p>
 *
 * <p>Couche <b>ws-rest</b> : contrôleurs REST. Ils traduisent HTTP en appels de la façade, sans logique métier.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.modules.quotas.ws.rest.QuotasController} ({@code GET /api/quotas}, {@code GET /api/quotas/{userId}}) pour le tableau de bord ;
 * {@link tn.eniso.computehive.modules.quotas.ws.rest.QuotasInternalController}, API interne ({@code /internal/quotas/**})
 * appelée par {@code quotas-service-rest-cli} en mode M/P.</p>
 */
package tn.eniso.computehive.modules.quotas.ws.rest;
