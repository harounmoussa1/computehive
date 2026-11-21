/**
 * <p>Module <b>jobs</b> — cycle de vie des jobs soumis par les utilisateurs.</p>
 *
 * <p>Couche <b>service-api</b> : façade du module, son unique contrat public. Les autres modules et la couche WS ne voient que cette interface.</p>
 *
 * <p>Contenu prévu : façade {@code JobsModule} (submit, cancel, findById, findByQuery, updateStatus, updateProgress).</p>
 */
package tn.eniso.computehive.modules.jobs.service.api;
