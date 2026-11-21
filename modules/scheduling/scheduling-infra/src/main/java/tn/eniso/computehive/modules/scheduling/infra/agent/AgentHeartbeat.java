package tn.eniso.computehive.modules.scheduling.infra.agent;

import java.util.List;

/**
 * Signe de vie périodique d'un agent ({@code Heartbeat} du proto). Les mesures GPU sont {@code null}
 * pour un worker CPU.
 *
 * @param running jobs que l'agent fait tourner : le serveur arrête ceux dont la tentative est périmée
 */
public record AgentHeartbeat(Long gpuMemoryFreeMb, Integer gpuUtilizationPercent, List<RunningAttempt> running) {

    public AgentHeartbeat {
        running = running == null ? List.of() : List.copyOf(running);
    }
}
