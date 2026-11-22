/**
 * <p>Module <b>jobs</b> — cycle de vie des jobs soumis par les utilisateurs.</p>
 *
 * <p>Couche <b>service-rest-cli</b> : proxy REST qui implémente la façade quand le module tourne dans un autre processus (mode M/P).</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.modules.jobs.service.restcli.JobsModuleRestClient}, qui appelle l'API interne du module
 * via {@code RemoteModuleClient} (core-service-rest-cli), et sa configuration Spring
 * ({@code computehive.remote.jobs.url}). Utilisé par {@code app-scheduling} en mode M/P.</p>
 */
package tn.eniso.computehive.modules.jobs.service.restcli;
