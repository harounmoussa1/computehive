package tn.eniso.computehive.modules.machines.service.impl;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Réglages du module machines ({@code computehive.machines.*}).
 *
 * @param heartbeatInterval  fréquence des heartbeats demandée aux agents
 * @param leaseTtl           délai sans heartbeat avant de déclarer une machine {@code DEAD}
 * @param detectionInterval  fréquence de la détection de panne
 * @param startupGrace       délai après le démarrage du serveur pendant lequel aucune machine
 *                           n'est déclarée {@code DEAD}, le temps que les agents se reconnectent
 */
@ConfigurationProperties("computehive.machines")
public record MachinesProperties(
        @DefaultValue("10s") Duration heartbeatInterval,
        @DefaultValue("30s") Duration leaseTtl,
        @DefaultValue("10s") Duration detectionInterval,
        @DefaultValue("30s") Duration startupGrace) {

    public MachinesProperties {
        if (heartbeatInterval.isNegative() || heartbeatInterval.isZero()) {
            throw new IllegalArgumentException("computehive.machines.heartbeat-interval must be positive");
        }
        if (leaseTtl.compareTo(heartbeatInterval.multipliedBy(2)) < 0) {
            throw new IllegalArgumentException(
                    "computehive.machines.lease-ttl must be at least twice the heartbeat interval");
        }
        if (detectionInterval.isNegative() || detectionInterval.isZero()) {
            throw new IllegalArgumentException("computehive.machines.detection-interval must be positive");
        }
        if (startupGrace.isNegative()) {
            throw new IllegalArgumentException("computehive.machines.startup-grace must not be negative");
        }
    }

    /** Valeurs par défaut : heartbeat 10 s, bail 30 s, détection toutes les 10 s, grâce 30 s. */
    public static MachinesProperties defaults() {
        return new MachinesProperties(Duration.ofSeconds(10), Duration.ofSeconds(30),
                Duration.ofSeconds(10), Duration.ofSeconds(30));
    }
}
