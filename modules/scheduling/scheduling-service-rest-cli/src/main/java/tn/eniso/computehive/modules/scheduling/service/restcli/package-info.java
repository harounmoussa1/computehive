/**
 * <p>Module <b>scheduling</b> — placement des jobs, réattribution, fencing et dialogue avec les agents.</p>
 *
 * <p>Couche <b>service-rest-cli</b> : proxy REST qui implémente la façade quand le module tourne dans un autre processus (mode M/P).</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.modules.scheduling.service.restcli.SchedulingModuleRestClient}, qui appelle l'API interne du module
 * via {@code RemoteModuleClient} (core-service-rest-cli), et sa configuration Spring
 * ({@code computehive.remote.scheduling.url}). Utilisé par {@code app-main} en mode M/P.</p>
 */
package tn.eniso.computehive.modules.scheduling.service.restcli;
