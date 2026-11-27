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
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.drivers.storageremote.RemoteBucket.ObjectInfo;
import tn.eniso.computehive.drivers.storageremote.RemoteBucket.RemoteUnavailableException;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointKey;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointStorageException;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointStore;
import tn.eniso.computehive.core.infra.util.Sha256;
import tn.eniso.computehive.extensions.checkpoints.api.StoredCheckpoint;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Checkpoints sur un stockage objet distant ({@link RemoteBucket} : compatible S3 en déploiement, simulé dans les tests),
 * protégé par Resilience4j.
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
 *
 * <p>Le contenu reçu est d'abord écrit dans un fichier temporaire, en calculant son empreinte : la mémoire ne
 * dépend pas de la taille du fichier, et chaque nouvel essai renvoie exactement les mêmes octets.</p>
 */
public class RemoteCheckpointStore implements CheckpointStore {

    private static final Logger log = LoggerFactory.getLogger(RemoteCheckpointStore.class);
    private static final String NAME = "checkpoint-storage";

    private final RemoteBucket bucket;
    private final Clock clock;
    /** Registres d'un seul élément : c'est par eux que Resilience4j publie ses métriques ({@link #bindTo}). */
    private final RetryRegistry retries;
    private final CircuitBreakerRegistry circuits;
    private final Retry retry;
    private final CircuitBreaker circuit;

    public RemoteCheckpointStore(RemoteBucket bucket, RemoteStorageProperties properties, Clock clock) {
        this.bucket = bucket;
        this.clock = clock;
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
                log.warn("Remote checkpoint storage circuit {}", event.getStateTransition()));
        retry.getEventPublisher().onRetry(event ->
                log.info("Remote checkpoint storage call failed, retry {} in {}", event.getNumberOfRetryAttempts(),
                        event.getWaitInterval()));
    }

    @Override
    public StoredCheckpoint store(CheckpointKey key, InputStream content) {
        Path spool = null;
        try {
            spool = Files.createTempFile("computehive-checkpoint-", ".part");
            ObjectInfo info;
            try (Sha256.DigestingStream digesting = new Sha256.DigestingStream(content)) {
                Files.copy(digesting, spool, StandardCopyOption.REPLACE_EXISTING);
                info = new ObjectInfo(digesting.sha256(), digesting.size(), clock.instant());
            }
            Path file = spool;
            resilient("store " + key, () -> {
                bucket.put(key.path(), file, info);
                return null;
            });
            log.info("Checkpoint {} stored remotely ({} bytes, sha256 {})", key, info.sizeBytes(), info.sha256());
            return toStored(key, info);
        } catch (IOException e) {
            throw new CheckpointStorageException("Checkpoint " + key + " could not be received", e);
        } finally {
            deleteQuietly(spool);
        }
    }

    @Override
    public Optional<StoredCheckpoint> find(CheckpointKey key) {
        return resilient("find " + key, () -> bucket.stat(key.path())).map(info -> toStored(key, info));
    }

    @Override
    public InputStream open(CheckpointKey key) {
        return resilient("open " + key, () -> bucket.open(key.path()))
                .orElseThrow(() -> new NotFoundException("Checkpoint", key.path()));
    }

    /** État du circuit ({@code CLOSED}, {@code OPEN}, {@code HALF_OPEN}), pour la supervision et les tests. */
    public CircuitBreaker.State circuitState() {
        return circuit.getState();
    }

    /**
     * Publie l'état du circuit et les résultats des appels ({@code resilience4j_circuitbreaker_*},
     * {@code resilience4j_retry_*}, étiquette {@code name="checkpoint-storage"} ; ADR 0014).
     */
    public void bindTo(MeterRegistry registry) {
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuits).bindTo(registry);
        TaggedRetryMetrics.ofRetryRegistry(retries).bindTo(registry);
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

    private static StoredCheckpoint toStored(CheckpointKey key, ObjectInfo info) {
        return new StoredCheckpoint(key, info.sha256(), info.sizeBytes(), info.storedAt());
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Temporary file {} could not be deleted", file, e);
        }
    }
}
