/**
 * <p>Module <b>machines</b> — inventaire des pc workers, heartbeats et détection de panne.</p>
 *
 * <p>Couche <b>service-api</b> : façade du module, son unique contrat public. Les autres modules et la couche WS ne voient que cette interface.</p>
 *
 * <p>Contenu prévu : façade {@code MachinesModule} (register, heartbeat, findAvailable(requirements), markDead).</p>
 */
package tn.eniso.computehive.modules.machines.service.api;
