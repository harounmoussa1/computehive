package tn.eniso.computehive.modules.scheduling.service.impl;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Réglages du module scheduling ({@code computehive.scheduling.*}).
 *
 * @param placementInterval         fréquence de placement des jobs en attente
 * @param launchAckTimeout          délai sans {@code CommandAck} avant de renvoyer un {@code LaunchJob} (protocole §6.5 : 15 s)
 * @param maxLaunchSends            nombre d'envois d'un {@code LaunchJob} avant d'abandonner la tentative
 * @param launchWatchdogInterval    fréquence de vérification des {@code LaunchJob} sans réponse
 * @param processedMessageRetention durée de conservation des {@code message_id} traités (protocole §6.4 : 24 h)
 * @param purgeInterval             fréquence de purge de ces traces
 * @param reconciliationInterval    fréquence de la réconciliation (réparation de l'état si un événement s'est perdu)
 * @param lostJobGrace              délai après la confirmation d'une tentative avant de la déclarer perdue si
 *                                  l'agent ne la déclare pas dans ses heartbeats
 */
@ConfigurationProperties("computehive.scheduling")
public record SchedulingProperties(
        @DefaultValue("2s") Duration placementInterval,
        @DefaultValue("15s") Duration launchAckTimeout,
        @DefaultValue("4") int maxLaunchSends,
        @DefaultValue("5s") Duration launchWatchdogInterval,
        @DefaultValue("24h") Duration processedMessageRetention,
        @DefaultValue("1h") Duration purgeInterval,
        @DefaultValue("15s") Duration reconciliationInterval,
        @DefaultValue("30s") Duration lostJobGrace) {

    public SchedulingProperties {
        requirePositive(placementInterval, "placement-interval");
        requirePositive(launchAckTimeout, "launch-ack-timeout");
        requirePositive(launchWatchdogInterval, "launch-watchdog-interval");
        requirePositive(processedMessageRetention, "processed-message-retention");
        requirePositive(purgeInterval, "purge-interval");
        requirePositive(reconciliationInterval, "reconciliation-interval");
        requirePositive(lostJobGrace, "lost-job-grace");
        if (maxLaunchSends < 1) {
            throw new IllegalArgumentException("computehive.scheduling.max-launch-sends must be at least 1");
        }
    }

    /**
     * Valeurs par défaut : placement toutes les 2 s, renvoi après 15 s, 4 envois au plus, traces gardées 24 h,
     * réconciliation toutes les 15 s, job déclaré perdu après 30 s d'absence des heartbeats.
     */
    public static SchedulingProperties defaults() {
        return new SchedulingProperties(Duration.ofSeconds(2), Duration.ofSeconds(15), 4, Duration.ofSeconds(5),
                Duration.ofHours(24), Duration.ofHours(1), Duration.ofSeconds(15), Duration.ofSeconds(30));
    }

    private static void requirePositive(Duration value, String name) {
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException("computehive.scheduling." + name + " must be positive");
        }
    }
}
