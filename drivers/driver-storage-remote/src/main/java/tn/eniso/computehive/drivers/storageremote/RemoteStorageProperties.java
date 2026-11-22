package tn.eniso.computehive.drivers.storageremote;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Réglages du stockage distant simulé et de sa protection ({@code computehive.checkpoints.remote.*}).
 *
 * @param latency                  délai simulé de chaque appel
 * @param failureRate              proportion d'appels qui échouent au hasard (0 : jamais, 1 : toujours)
 * @param retryMaxAttempts         nombre total d'essais d'un appel en échec
 * @param retryWait                attente avant le 2e essai, doublée ensuite
 * @param circuitWindowSize        nombre des derniers appels sur lesquels le taux d'échec est calculé
 * @param circuitFailureRateThreshold taux d'échec (en %) au-delà duquel le circuit s'ouvre
 * @param circuitOpenDuration      durée pendant laquelle un circuit ouvert refuse tout appel
 */
@ConfigurationProperties("computehive.checkpoints.remote")
public record RemoteStorageProperties(
        @DefaultValue("50ms") Duration latency,
        @DefaultValue("0.0") double failureRate,
        @DefaultValue("3") int retryMaxAttempts,
        @DefaultValue("200ms") Duration retryWait,
        @DefaultValue("10") int circuitWindowSize,
        @DefaultValue("50") float circuitFailureRateThreshold,
        @DefaultValue("10s") Duration circuitOpenDuration) {

    public static RemoteStorageProperties defaults() {
        return new RemoteStorageProperties(Duration.ofMillis(50), 0.0, 3, Duration.ofMillis(200), 10, 50,
                Duration.ofSeconds(10));
    }
}
