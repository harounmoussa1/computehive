/**
 * <p>Module <b>quotas</b> — consommation gpu par utilisateur et priorité de placement.</p>
 *
 * <p>Couche <b>ws-rest</b> : contrôleurs REST. Ils traduisent HTTP en appels de la façade, sans logique métier.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.modules.quotas.ws.rest.QuotasController} ({@code GET /api/quotas}, {@code GET /api/quotas/{userId}}) pour le tableau de bord.</p>
 */
package tn.eniso.computehive.modules.quotas.ws.rest;
