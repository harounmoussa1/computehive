/**
 * <p>Module <b>scheduling</b> — placement des jobs, réattribution, fencing et dialogue avec les agents.</p>
 *
 * <p>Couche <b>infra</b> : entités du domaine, DTO et énumérations. Base de la pyramide, elle ne dépend que de {@code core-infra}.</p>
 *
 * <p>Contenu prévu : {@code Assignment} (job {@code JobRef}, machine {@code MachineRef}, attemptId, état), {@code ProcessedAgentMessage} (dédoublonnage).</p>
 */
package tn.eniso.computehive.modules.scheduling.infra;
