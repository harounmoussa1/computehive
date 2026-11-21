package tn.eniso.computehive.modules.scheduling.infra.agent;

import tn.eniso.computehive.core.infra.util.Checks;

/**
 * Réponse d'un agent à un ordre du serveur ({@code CommandAck} du proto).
 *
 * @param commandId {@code command_id} de l'ordre
 * @param accepted  {@code false} si l'agent refuse, ex. « GPU déjà occupé »
 * @param reason    motif du refus
 */
public record CommandAckReport(String commandId, boolean accepted, String reason) {

    public CommandAckReport {
        Checks.requireNonBlank(commandId, "commandId");
    }
}
