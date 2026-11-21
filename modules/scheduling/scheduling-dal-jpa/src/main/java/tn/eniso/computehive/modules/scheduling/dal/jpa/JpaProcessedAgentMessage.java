package tn.eniso.computehive.modules.scheduling.dal.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Entité de stockage de {@code ProcessedAgentMessage} : table {@code scheduling.processed_agent_message}.
 * Les écritures passent par une requête {@code INSERT … ON CONFLICT DO NOTHING} ; l'entité sert à la
 * validation du schéma par Hibernate et aux lectures.
 */
@Entity
@Table(schema = "scheduling", name = "processed_agent_message")
class JpaProcessedAgentMessage {

    @Id
    @Column(name = "message_id")
    private String messageId;

    @Column(name = "machine_id", nullable = false)
    private String machineId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected JpaProcessedAgentMessage() {
    }
}
