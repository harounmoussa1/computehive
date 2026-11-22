/**
 * <p>Module <b>machines</b> — inventaire des pc workers, heartbeats et détection de panne.</p>
 *
 * <p>Couche <b>service-rest-cli</b> : proxy REST qui implémente la façade quand le module tourne dans un autre processus (mode M/P).</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.modules.machines.service.restcli.MachinesModuleRestClient}, qui appelle l'API interne du module
 * via {@code RemoteModuleClient} (core-service-rest-cli), et sa configuration Spring
 * ({@code computehive.remote.machines.url}). Utilisé par {@code app-scheduling} en mode M/P.</p>
 */
package tn.eniso.computehive.modules.machines.service.restcli;
