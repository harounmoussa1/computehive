/**
 * <p>Module <b>scheduling</b> — placement des jobs, réattribution, fencing et dialogue avec les agents.</p>
 *
 * <p>Couche <b>ws-rest</b> : contrôleurs REST. Ils traduisent HTTP en appels de la façade, sans logique métier.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.modules.scheduling.ws.rest.SchedulingInternalController}, API interne
 * ({@code /internal/scheduling/**}) appelée par {@code scheduling-service-rest-cli} en mode M/P.</p>
 */
package tn.eniso.computehive.modules.scheduling.ws.rest;
