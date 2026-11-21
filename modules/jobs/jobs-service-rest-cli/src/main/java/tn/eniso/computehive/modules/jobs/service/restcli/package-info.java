/**
 * <p>Module <b>jobs</b> — cycle de vie des jobs soumis par les utilisateurs.</p>
 *
 * <p>Couche <b>service-rest-cli</b> : proxy REST qui implémente la façade quand le module tourne dans un autre processus (mode M/P).</p>
 *
 * <p>Utilisé par {@code app-scheduling} en mode M/P pour appeler {@code JobsModule} à distance.</p>
 */
package tn.eniso.computehive.modules.jobs.service.restcli;
