/**
 * <p>Module <b>scheduling</b> — placement des jobs, réattribution, fencing et dialogue avec les agents.</p>
 *
 * <p>Couche <b>dal-jpa</b> : implémentation JPA (Hibernate, PostgreSQL) des repositories de {@code dal-api}. Les tables appartiennent à ce module seul.</p>
 *
 * <p>Contenu : {@code JpaAssignment} (table {@code scheduling.assignment} ; index uniques partiels : une tentative active par job et par machine), {@code JpaProcessedAgentMessage} (table {@code scheduling.processed_agent_message}, insertion atomique {@code ON CONFLICT DO NOTHING}), leurs adaptateurs, et {@link tn.eniso.computehive.modules.scheduling.dal.jpa.SchedulingJpaConfiguration} (schéma et migrations Flyway {@code db/migration/scheduling}).</p>
 */
package tn.eniso.computehive.modules.scheduling.dal.jpa;
