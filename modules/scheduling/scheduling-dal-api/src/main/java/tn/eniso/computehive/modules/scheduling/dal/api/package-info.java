/**
 * <p>Module <b>scheduling</b> — placement des jobs, réattribution, fencing et dialogue avec les agents.</p>
 *
 * <p>Couche <b>dal-api</b> : interfaces des repositories. Chaque repository appartient à exactement un service CRUD du {@code service-impl}.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.modules.scheduling.dal.api.AssignmentRepository} ({@code AssignmentCrudService}), {@link tn.eniso.computehive.modules.scheduling.dal.api.ProcessedAgentMessageRepository} ({@code ProcessedAgentMessageCrudService}).</p>
 */
package tn.eniso.computehive.modules.scheduling.dal.api;
