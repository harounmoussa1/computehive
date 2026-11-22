package tn.eniso.computehive.core.service.restcli;

import tn.eniso.computehive.core.infra.util.Checks;

import java.time.Duration;

/**
 * Réglages des appels vers un module distant : délais, retries et circuit breaker.
 *
 * @param connectTimeout       délai pour établir la connexion
 * @param readTimeout          délai pour recevoir la réponse
 * @param maxAttempts          nombre total de tentatives (1 = aucun retry)
 * @param retryWait            pause entre deux tentatives
 * @param failureRateThreshold pourcentage d'échecs qui ouvre le circuit
 * @param slidingWindowSize    nombre de derniers appels pris en compte par le circuit
 * @param minimumCalls         nombre d'appels avant que le circuit puisse s'ouvrir
 * @param openStateWait        durée pendant laquelle le circuit ouvert refuse tout appel
 */
public record RemoteCallPolicy(Duration connectTimeout, Duration readTimeout, int maxAttempts, Duration retryWait,
                               float failureRateThreshold, int slidingWindowSize, int minimumCalls,
                               Duration openStateWait) {

    public RemoteCallPolicy {
        Checks.requireNonNull(connectTimeout, "connectTimeout");
        Checks.requireNonNull(readTimeout, "readTimeout");
        Checks.requireNonNull(retryWait, "retryWait");
        Checks.requireNonNull(openStateWait, "openStateWait");
        Checks.require(maxAttempts >= 1, "maxAttempts must be at least 1");
        Checks.require(failureRateThreshold > 0 && failureRateThreshold <= 100,
                "failureRateThreshold must be in ]0, 100]");
        Checks.require(slidingWindowSize >= 1 && minimumCalls >= 1, "the circuit breaker window must not be empty");
    }

    /** Valeurs par défaut, adaptées à deux applications sur le même serveur. */
    public static RemoteCallPolicy defaults() {
        return new RemoteCallPolicy(Duration.ofSeconds(2), Duration.ofSeconds(5), 3, Duration.ofMillis(200),
                50, 10, 5, Duration.ofSeconds(10));
    }
}
