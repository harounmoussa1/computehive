package tn.eniso.computehive.modules.scheduling.dal.jpa;

import org.springframework.stereotype.Repository;
import tn.eniso.computehive.modules.scheduling.dal.api.ProcessedAgentMessageRepository;
import tn.eniso.computehive.modules.scheduling.infra.ProcessedAgentMessage;

import java.time.Instant;

/** Implémentation JPA de {@link ProcessedAgentMessageRepository}. */
@Repository
class JpaProcessedAgentMessageRepository implements ProcessedAgentMessageRepository {

    private final SpringDataProcessedAgentMessageRepository repository;

    JpaProcessedAgentMessageRepository(SpringDataProcessedAgentMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean markProcessed(ProcessedAgentMessage message) {
        return repository.insertIfAbsent(message.messageId(), message.machineId(), message.processedAt()) == 1;
    }

    @Override
    public boolean isProcessed(String messageId) {
        return repository.existsById(messageId);
    }

    @Override
    public int deleteProcessedBefore(Instant cutoff) {
        return repository.deleteProcessedBefore(cutoff);
    }
}
