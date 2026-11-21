/**
 * <p>Module <b>jobs</b> — cycle de vie des jobs soumis par les utilisateurs.</p>
 *
 * <p>Couche <b>service-api</b> : façade du module, son unique contrat public. Les autres modules et la couche WS ne voient que cette interface.</p>
 *
 * <p>Contenu : façade {@link tn.eniso.computehive.modules.jobs.service.api.JobsModule} (submit, cancel, findById, findByQuery, updateStatus, updateProgress, recordCheckpoint) et topics publiés {@code JobsEvents} ({@code jobs.job-cancelled}).</p>
 */
package tn.eniso.computehive.modules.jobs.service.api;
