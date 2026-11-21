package tn.eniso.computehive.modules.machines.infra;

import java.time.Duration;

/**
 * Règles du bail fixées par le serveur et envoyées à l'agent dans {@code Registered}.
 *
 * @param heartbeatInterval fréquence d'envoi des heartbeats par l'agent
 * @param leaseTtl          délai sans heartbeat au-delà duquel la machine passe {@code DEAD}
 */
public record LeasePolicy(Duration heartbeatInterval, Duration leaseTtl) {
}
