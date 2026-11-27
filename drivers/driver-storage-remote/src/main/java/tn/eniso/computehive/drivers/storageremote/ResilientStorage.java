package tn.eniso.computehive.drivers.storageremote;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tn.eniso.computehive.drivers.storageremote.RemoteBucket.RemoteUnavailableException;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Protection Resilience4j des appels au stockage distant, <b>partagée</b> par les checkpoints et les fichiers : c'est
 * le même service, il a donc un seul circuit.
 *
 * <ul>
 *   <li><b>Retry</b> : une panne passagère est réessayée, avec une attente qui double à chaque essai ;</li>
 *   <li><b>Circuit breaker</b> : si trop d'appels récents échouent, le circuit s'<i>ouvre</i> et refuse aussitôt
 *       les appels pendant un temps, au lieu de faire attendre chaque agent et de surcharger un service déjà en
 *       difficulté ; il laisse ensuite passer quelques appels d'essai (<i>demi-ouvert</i>) et se referme si le
 *       service est revenu.</li>
 * </ul>
 *
 * <p>Le retry entoure le circuit : chaque essai compte pour le circuit, et un circuit ouvert n'est pas réessayé.</p>
 */
public class ResilientStorage {

    /** Nom du circuit et du retry dans les métriques ({@code name="object-storage"}). */
    static final String NAME = "object-storage";

    private static final Logger log = LoggerFactory.getLogger(ResilientStorage.class);

    /** Registres d'un seul élément : c'est par eux que Resilience4j publie ses métriques ({@link #bindTo}). */
    private final RetryRegistry retries;
    private final CircuitBreakerRegistry circuits;
    private final Retry retry;
    private final CircuitBreaker circuit;

    public ResilientStorage(RemoteStorageProperties properties) {
        this.circuits = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.circuitWindowSize())
                .minimumNumberOfCalls(Math.min(5, properties.circuitWindowSize()))
                .failureRateThreshold(properties.circuitFailureRateThreshold())
                .waitDurationInOpenState(properties.circuitOpenDuration())
                .permittedNumberOfCallsInHalfOpenState(2)
                .recordExceptions(RemoteUnavailableException.class)
                .build());
        this.circuit = circuits.circuitBreaker(NAME);
        this.retries = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(properties.retryMaxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(properties.retryWait(), 2))
                .retryExceptions(RemoteUnavailableException.class)
                .ignoreExceptions(CallNotPermittedException.class)
                .build());
        this.retry = retries.retry(NAME);
        circuit.getEventPublisher().onStateTransition(event ->
                log.warn("Remote storage circuit {}", event.getStateTransition()));
        retry.getEventPublisher().onRetry(event ->
                log.info("Remote storage call failed, retry {} in {}", event.getNumberOfRetryAttempts(),
                        event.getWaitInterval()));
    }

    /**
     * Exécute un appel protégé. Un échec définitif (circuit ouvert, ou panne qui dure après tous les essais) devient
     * l'exception de l'appelant, construite par {@code failure} à partir d'un message et de la cause.
     */
    public <T> T call(String operation, Supplier<T> call,
                      BiFunction<String, Throwable, ? extends RuntimeException> failure) {
        try {
            return Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuit, call)).get();
        } catch (CallNotPermittedException e) {
            throw failure.apply("Remote storage unavailable (circuit open): " + operation, e);
        } catch (RemoteUnavailableException e) {
            throw failure.apply("Remote storage failed after " + retry.getRetryConfig().getMaxAttempts()
                    + " attempts: " + operation, e);
        }
    }

    /** État du circuit ({@code CLOSED}, {@code OPEN}, {@code HALF_OPEN}), pour la supervision et les tests. */
    public CircuitBreaker.State circuitState() {
        return circuit.getState();
    }

    /**
     * Publie l'état du circuit et les résultats des appels ({@code resilience4j_circuitbreaker_*},
     * {@code resilience4j_retry_*}, étiquette {@code name="object-storage"} ; ADR 0014).
     */
    public void bindTo(MeterRegistry registry) {
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuits).bindTo(registry);
        TaggedRetryMetrics.ofRetryRegistry(retries).bindTo(registry);
    }
}
