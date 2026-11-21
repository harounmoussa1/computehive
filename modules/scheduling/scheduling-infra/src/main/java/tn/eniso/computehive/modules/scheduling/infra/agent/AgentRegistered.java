package tn.eniso.computehive.modules.scheduling.infra.agent;

import java.time.Duration;

/**
 * Réponse du serveur à l'inscription d'un agent ({@code Registered} du proto) : son identifiant de
 * machine et les règles du bail.
 *
 * @param heartbeatInterval fréquence des heartbeats demandée à l'agent
 * @param leaseTtl          délai sans heartbeat au-delà duquel la machine est déclarée {@code DEAD}
 */
public record AgentRegistered(String machineId, Duration heartbeatInterval, Duration leaseTtl) {
}
