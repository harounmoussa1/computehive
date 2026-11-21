package tn.eniso.computehive.modules.scheduling.dal.api;

import tn.eniso.computehive.modules.scheduling.infra.ProcessedAgentMessage;

import java.time.Instant;

/** Trace des messages d'agent déjà traités. Seul {@code ProcessedAgentMessageCrudService} l'utilise. */
public interface ProcessedAgentMessageRepository {

    /**
     * Enregistre le message comme traité, de façon atomique.
     *
     * @return {@code true} s'il ne l'était pas encore, {@code false} si c'est un doublon
     */
    boolean markProcessed(ProcessedAgentMessage message);

    boolean isProcessed(String messageId);

    /** Supprime les traces antérieures à {@code cutoff} ; renvoie le nombre de traces supprimées. */
    int deleteProcessedBefore(Instant cutoff);
}
