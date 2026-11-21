package tn.eniso.computehive.modules.scheduling.infra;

import tn.eniso.computehive.core.infra.util.Checks;

import java.time.Instant;

/**
 * Trace d'un message d'agent déjà traité (table {@code processed_agent_message}).
 *
 * <p>L'outbox de l'agent renvoie les messages non acquittés après une coupure. Garder leur
 * {@code message_id} permet de ne jamais traiter deux fois le même, par exemple un {@code Finished}
 * qui ferait compter deux fois le temps GPU (docs/worker-protocol.md §6.4). Les traces sont purgées
 * après 24 h.</p>
 *
 * @param messageId   {@code message_id} généré par l'agent
 * @param machineId   machine qui a envoyé le message
 * @param processedAt date du traitement
 */
public record ProcessedAgentMessage(String messageId, String machineId, Instant processedAt) {

    public static final int MAX_MESSAGE_ID_LENGTH = 128;

    public ProcessedAgentMessage {
        Checks.requireNonBlank(messageId, "messageId");
        Checks.require(messageId.length() <= MAX_MESSAGE_ID_LENGTH,
                "messageId must not exceed " + MAX_MESSAGE_ID_LENGTH + " characters");
        Checks.requireNonBlank(machineId, "machineId");
        Checks.requireNonNull(processedAt, "processedAt");
    }
}
