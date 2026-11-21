/**
 * <p>Module <b>scheduling</b> — placement des jobs, réattribution, fencing et dialogue avec les agents.</p>
 *
 * <p>Couche <b>service-api</b> : façade du module, son unique contrat public. Les autres modules et la couche WS ne voient que cette interface.</p>
 *
 * <p>Contenu : façade {@link tn.eniso.computehive.modules.scheduling.service.api.SchedulingModule} (registerAgent, heartbeat, reportProgress, reportCheckpoint, reportFinished, acknowledge, currentAttempt, attemptsOf) et port sortant {@link tn.eniso.computehive.modules.scheduling.service.api.AgentChannel}, implémenté par {@code ws-grpc}.</p>
 */
package tn.eniso.computehive.modules.scheduling.service.api;
