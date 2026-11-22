package tn.eniso.computehive.drivers.storageremote;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.drivers.storageremote.SimulatedRemoteBucket.RemoteObject;
import tn.eniso.computehive.drivers.storageremote.SimulatedRemoteBucket.RemoteUnavailableException;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointKey;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointStorageException;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointStore;
import tn.eniso.computehive.extensions.checkpoints.api.Sha256;
import tn.eniso.computehive.extensions.checkpoints.api.StoredCheckpoint;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Checkpoints sur un stockage distant (simulé), protégé par Resilience4j.
 *
 * <ul>
 *   <li><b>Retry</b> : une panne passagère est réessayée, avec une attente qui double à chaque essai ;</li>
 *   <li><b>Circuit breaker</b> : si trop d'appels récents échouent, le circuit s'<i>ouvre</i> et refuse aussitôt
 *       les appels pendant un temps, au lieu de faire attendre chaque agent et de surcharger un service déjà en
 *       difficulté ; il laisse ensuite passer quelques appels d'essai (<i>demi-ouvert</i>) et se referme si le
 *       service est revenu.</li>
 * </ul>
 *
 * <p>Le retry entoure le circuit : chaque essai compte pour le circuit, et un circuit ouvert n'est pas réessayé.
 * Un échec définitif devient une {@link CheckpointStorageException}.</p>
 */
public class RemoteCheckpointStore implements CheckpointStore {

    private static final Logger log = LoggerFactory.getLogger(RemoteCheckpointStore.class);

    private final SimulatedRemoteBucket bucket;
    private final Clock clock;
    private final Retry retry;
    private final CircuitBreaker circuit;

    public RemoteCheckpointStore(SimulatedRemoteBucket bucket, RemoteStorageProperties properties, Clock clock) {
        this.bucket = bucket;
        this.clock = clock;
        this.circuit = CircuitBreaker.of("checkpoint-storage", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.circuitWindowSize())
                .minimumNumberOfCalls(Math.min(5, properties.circuitWindowSize()))
                .failureRateThreshold(properties.circuitFailureRateThreshold())
                .waitDurationInOpenState(properties.circuitOpenDuration())
                .permittedNumberOfCallsInHalfOpenState(2)
                .recordExceptions(RemoteUnavailableException.class)
                .build());
        this.retry = Retry.of("checkpoint-storage", RetryConfig.custom()
                .maxAttempts(properties.retryMaxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(properties.retryWait(), 2))
                .retryExceptions(RemoteUnavailableException.class)
                .ignoreExceptions(CallNotPermittedException.class)
                .build());
        circuit.getEventPublisher().onStateTransition(event ->
                log.warn("Remote checkpoint storage circuit {}", event.getStateTransition()));
        retry.getEventPublisher().onRetry(event ->
                log.info("Remote checkpoint storage call failed, retry {} in {}", event.getNumberOfRetryAttempts(),
                        event.getWaitInterval()));
    }

    /** Le contenu est lu une fois (calcul de l'empreinte), puis envoyé ; un nouvel essai renvoie les mêmes octets. */
    @Override
    public StoredCheckpoint store(CheckpointKey key, InputStream content) {
        byte[] bytes;
        String sha256;
        try (Sha256.DigestingStream digesting = new Sha256.DigestingStream(content)) {
            bytes = digesting.readAllBytes();
            sha256 = digesting.sha256();
        } catch (IOException e) {
            throw new CheckpointStorageException("Checkpoint " + key + " could not be received", e);
        }
        RemoteObject object = new RemoteObject(bytes, sha256, clock.instant());
        resilient("store " + key, () -> {
            bucket.put(key.path(), object);
            return null;
        });
        log.info("Checkpoint {} stored remotely ({} bytes)", key, bytes.length);
        return toStored(key, object);
    }

    @Override
    public Optional<StoredCheckpoint> find(CheckpointKey key) {
        return resilient("find " + key, () -> bucket.get(key.path())).map(object -> toStored(key, object));
    }

    @Override
    public InputStream open(CheckpointKey key) {
        RemoteObject object = resilient("open " + key, () -> bucket.get(key.path()))
                .orElseThrow(() -> new NotFoundException("Checkpoint", key.path()));
        return new ByteArrayInputStream(object.content());
    }

    /** État du circuit ({@code CLOSED}, {@code OPEN}, {@code HALF_OPEN}), pour la supervision et les tests. */
    public CircuitBreaker.State circuitState() {
        return circuit.getState();
    }

    private <T> T resilient(String operation, Supplier<T> call) {
        try {
            return Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuit, call)).get();
        } catch (CallNotPermittedException e) {
            throw new CheckpointStorageException("Remote checkpoint storage unavailable (circuit open): " + operation, e);
        } catch (RemoteUnavailableException e) {
            throw new CheckpointStorageException("Remote checkpoint storage failed after "
                    + retry.getRetryConfig().getMaxAttempts() + " attempts: " + operation, e);
        }
    }

    private static StoredCheckpoint toStored(CheckpointKey key, RemoteObject object) {
        return new StoredCheckpoint(key, object.sha256(), object.content().length, object.storedAt());
    }
}
