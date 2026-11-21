/**
 * <p>Module <b>quotas</b> — consommation gpu par utilisateur et priorité de placement.</p>
 *
 * <p>Couche <b>service-rest-cli</b> : proxy REST qui implémente la façade quand le module tourne dans un autre processus (mode M/P).</p>
 *
 * <p>Utilisé par {@code app-scheduling} en mode M/P pour appeler {@code QuotasModule} à distance.</p>
 */
package tn.eniso.computehive.modules.quotas.service.restcli;
