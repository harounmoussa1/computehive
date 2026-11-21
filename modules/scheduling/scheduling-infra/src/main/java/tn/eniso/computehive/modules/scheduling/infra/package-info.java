/**
 * <p>Module <b>scheduling</b> — placement des jobs, réattribution, fencing et dialogue avec les agents.</p>
 *
 * <p>Couche <b>infra</b> : entités du domaine, DTO et énumérations. Base de la pyramide, elle ne dépend que de {@code core-infra}.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.modules.scheduling.infra.Assignment} (tentative d'exécution d'un job sur une machine ; son {@code attemptId} est le jeton de fencing), {@code AssignmentStatus}, {@code ResumePoint}, {@code AssignmentInfo}, {@code ProcessedAgentMessage} (dédoublonnage des messages d'agent).</p>
 */
package tn.eniso.computehive.modules.scheduling.infra;
