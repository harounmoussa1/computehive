/**
 * <p>Module <b>dashboard</b> — tableau de bord : vue d'ensemble des machines, des jobs et des quotas.</p>
 *
 * <p>Couche <b>ws-rest</b> : l'API du tableau de bord et la page web qui l'affiche.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.modules.dashboard.ws.rest.DashboardController}
 * ({@code GET /api/dashboard}, {@code GET /api/dashboard/jobs/{id}}) ; la page, servie à la racine de
 * l'application par Spring Boot : {@code static/index.html}, {@code static/dashboard/app.css},
 * {@code static/dashboard/app.js} (HTML, CSS et JavaScript sans outil de build ni bibliothèque externe).</p>
 */
package tn.eniso.computehive.modules.dashboard.ws.rest;
