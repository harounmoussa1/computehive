/**
 * <p>Module <b>jobs</b> — cycle de vie des jobs soumis par les utilisateurs.</p>
 *
 * <p>Couche <b>ws-rest</b> : contrôleurs REST. Ils traduisent HTTP en appels de la façade, sans logique métier.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.modules.jobs.ws.rest.JobsController} ({@code POST /api/jobs}, {@code DELETE /api/jobs/{id}}, {@code GET /api/jobs/{id}}, {@code GET /api/jobs?status=&owner=}) et le corps de requête {@code SubmitJobRequest} ;
 * {@link tn.eniso.computehive.modules.jobs.ws.rest.JobsInternalController}, API interne ({@code /internal/jobs/**})
 * appelée par {@code jobs-service-rest-cli} en mode M/P.</p>
 */
package tn.eniso.computehive.modules.jobs.ws.rest;
