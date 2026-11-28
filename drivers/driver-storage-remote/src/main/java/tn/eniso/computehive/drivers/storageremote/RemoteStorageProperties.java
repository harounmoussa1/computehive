package tn.eniso.computehive.drivers.storageremote;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Réglages du stockage distant et de sa protection ({@code computehive.checkpoints.remote.*}).
 *
 * @param s3                       service compatible S3 ; sans adresse, un stockage simulé en mémoire le remplace
 * @param latency                  délai de chaque appel, stockage simulé seulement
 * @param failureRate              proportion d'appels qui échouent au hasard (0 : jamais, 1 : toujours), stockage
 *                                 simulé seulement
 * @param retryMaxAttempts         nombre total d'essais d'un appel en échec
 * @param retryWait                attente avant le 2e essai, doublée ensuite
 * @param circuitWindowSize        nombre des derniers appels sur lesquels le taux d'échec est calculé
 * @param circuitFailureRateThreshold taux d'échec (en %) au-delà duquel le circuit s'ouvre
 * @param circuitOpenDuration      durée pendant laquelle un circuit ouvert refuse tout appel
 */
@ConfigurationProperties("computehive.checkpoints.remote")
public record RemoteStorageProperties(
        @DefaultValue S3 s3,
        @DefaultValue("50ms") Duration latency,
        @DefaultValue("0.0") double failureRate,
        @DefaultValue("3") int retryMaxAttempts,
        @DefaultValue("200ms") Duration retryWait,
        @DefaultValue("10") int circuitWindowSize,
        @DefaultValue("50") float circuitFailureRateThreshold,
        @DefaultValue("10s") Duration circuitOpenDuration) {

    /**
     * Accès au service compatible S3 ({@code computehive.checkpoints.remote.s3.*}).
     *
     * @param endpoint  adresse du service, ex. {@code http://seaweedfs:8333} ; vide : stockage simulé
     * @param accessKey identifiant d'accès
     * @param secretKey mot de passe d'accès
     * @param bucket    bucket des fichiers, créé au premier dépôt s'il n'existe pas
     */
    public record S3(@DefaultValue("") String endpoint, @DefaultValue("") String accessKey,
                     @DefaultValue("") String secretKey, @DefaultValue("computehive") String bucket) {

        public boolean configured() {
            return endpoint != null && !endpoint.isBlank();
        }
    }

    public static RemoteStorageProperties defaults() {
        return new RemoteStorageProperties(new S3("", "", "", "computehive"), Duration.ofMillis(50), 0.0, 3,
                Duration.ofMillis(200), 10, 50, Duration.ofSeconds(10));
    }
}
