/**
 * <p>Module <b>scheduling</b> — placement des jobs, réattribution, fencing et dialogue avec les agents.</p>
 *
 * <p>Couche <b>service-api</b> : façade du module, son unique contrat public. Les autres modules et la couche WS ne voient que cette interface.</p>
 *
 * <p>Contenu prévu : façade {@code SchedulingModule} (onAgentRegistered, onHeartbeat, reportProgress, reportCheckpoint, reportFinished, acknowledge).</p>
 */
package tn.eniso.computehive.modules.scheduling.service.api;
