/**
 * <p>Module <b>machines</b> — inventaire des pc workers, heartbeats et détection de panne.</p>
 *
 * <p>Couche <b>ws-rest</b> : contrôleurs REST. Ils traduisent HTTP en appels de la façade, sans logique métier.</p>
 *
 * <p>Contenu : {@code GET /api/machines} et {@code GET /api/machines/{id}} (tableau de bord) ;
 * {@link tn.eniso.computehive.modules.machines.ws.rest.MachinesInternalController}, API interne ({@code /internal/machines/**})
 * appelée par {@code machines-service-rest-cli} en mode M/P.</p>
 */
package tn.eniso.computehive.modules.machines.ws.rest;
