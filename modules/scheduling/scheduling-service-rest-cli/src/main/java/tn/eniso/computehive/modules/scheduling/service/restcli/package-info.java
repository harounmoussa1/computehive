/**
 * <p>Module <b>scheduling</b> — placement des jobs, réattribution, fencing et dialogue avec les agents.</p>
 *
 * <p>Couche <b>service-rest-cli</b> : proxy REST qui implémente la façade quand le module tourne dans un autre processus (mode M/P).</p>
 *
 * <p>Utilisé par {@code app-main} en mode M/P pour appeler {@code SchedulingModule} à distance.</p>
 */
package tn.eniso.computehive.modules.scheduling.service.restcli;
