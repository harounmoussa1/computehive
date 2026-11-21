package tn.eniso.computehive.modules.scheduling.service.api;

import tn.eniso.computehive.modules.scheduling.infra.agent.AgentCommand;

/**
 * Canal vers les agents connectés : envoie un ordre sur le stream ouvert par l'agent d'une machine.
 *
 * <p>Déclaré ici, implémenté par la couche {@code ws-grpc} ({@code AgentSessionRegistry}) :
 * {@code scheduling-service-impl} envoie des ordres sans dépendre de gRPC ni de la couche WS
 * (inversion de dépendance, conforme à la Convergence Rule).</p>
 */
public interface AgentChannel {

    /** {@code true} si l'agent de cette machine a un stream ouvert. */
    boolean isConnected(String machineId);

    /**
     * Écrit l'ordre sur le stream de la machine.
     *
     * @return {@code false} si la machine n'a pas de stream ouvert : l'ordre n'est pas envoyé
     */
    boolean send(String machineId, AgentCommand command);
}
