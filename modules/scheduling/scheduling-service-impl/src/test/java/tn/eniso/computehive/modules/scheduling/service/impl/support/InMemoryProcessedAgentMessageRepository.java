package tn.eniso.computehive.modules.scheduling.service.impl.support;

import tn.eniso.computehive.modules.scheduling.dal.api.ProcessedAgentMessageRepository;
import tn.eniso.computehive.modules.scheduling.infra.ProcessedAgentMessage;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Faux repository pour les tests unitaires : même contrat que l'implémentation JPA. */
public class InMemoryProcessedAgentMessageRepository implements ProcessedAgentMessageRepository {

    private final Map<String, ProcessedAgentMessage> rows = new LinkedHashMap<>();

    @Override
    public boolean markProcessed(ProcessedAgentMessage message) {
        return rows.putIfAbsent(message.messageId(), message) == null;
    }

    @Override
    public boolean isProcessed(String messageId) {
        return rows.containsKey(messageId);
    }

    @Override
    public int deleteProcessedBefore(Instant cutoff) {
        int before = rows.size();
        rows.values().removeIf(message -> message.processedAt().isBefore(cutoff));
        return before - rows.size();
    }
}
