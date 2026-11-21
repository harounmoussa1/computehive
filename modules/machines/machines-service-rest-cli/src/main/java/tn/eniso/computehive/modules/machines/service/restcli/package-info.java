/**
 * <p>Module <b>machines</b> — inventaire des pc workers, heartbeats et détection de panne.</p>
 *
 * <p>Couche <b>service-rest-cli</b> : proxy REST qui implémente la façade quand le module tourne dans un autre processus (mode M/P).</p>
 *
 * <p>Utilisé par {@code app-scheduling} en mode M/P pour appeler {@code MachinesModule} à distance.</p>
 */
package tn.eniso.computehive.modules.machines.service.restcli;
