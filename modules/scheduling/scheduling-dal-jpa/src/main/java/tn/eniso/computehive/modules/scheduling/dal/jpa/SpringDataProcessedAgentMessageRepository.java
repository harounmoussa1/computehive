package tn.eniso.computehive.modules.scheduling.dal.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/** Repository Spring Data, interne à la couche dal-jpa. */
interface SpringDataProcessedAgentMessageRepository extends JpaRepository<JpaProcessedAgentMessage, String> {

    /**
     * Insertion atomique : deux traitements concurrents du même message ne peuvent pas
     * réussir tous les deux. Renvoie 1 si la ligne a été insérée, 0 si elle existait déjà.
     */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            insert into scheduling.processed_agent_message (message_id, machine_id, processed_at)
            values (:messageId, :machineId, :processedAt)
            on conflict (message_id) do nothing
            """)
    int insertIfAbsent(@Param("messageId") String messageId, @Param("machineId") String machineId,
                       @Param("processedAt") Instant processedAt);

    @Modifying
    @Query("delete from JpaProcessedAgentMessage m where m.processedAt < :cutoff")
    int deleteProcessedBefore(@Param("cutoff") Instant cutoff);
}
