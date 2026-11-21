package tn.eniso.computehive.modules.scheduling.service.impl.entity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.eniso.computehive.modules.scheduling.dal.api.ProcessedAgentMessageRepository;
import tn.eniso.computehive.modules.scheduling.infra.ProcessedAgentMessage;

import java.time.Instant;

/**
 * Seul propriétaire de {@link ProcessedAgentMessageRepository}. Ces traces sont purement techniques
 * (dédoublonnage) : aucun autre module n'a à y réagir, elles ne sont donc pas annoncées aux intercepteurs.
 */
@Service
public class ProcessedAgentMessageCrudService {

    private final ProcessedAgentMessageRepository repository;

    public ProcessedAgentMessageCrudService(ProcessedAgentMessageRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean isProcessed(String messageId) {
        return repository.isProcessed(messageId);
    }

    /** @return {@code false} si le message était déjà marqué (traitement concurrent d'un doublon) */
    @Transactional
    public boolean markProcessed(String messageId, String machineId, Instant now) {
        return repository.markProcessed(new ProcessedAgentMessage(messageId, machineId, now));
    }

    @Transactional
    public int purgeBefore(Instant cutoff) {
        return repository.deleteProcessedBefore(cutoff);
    }
}
