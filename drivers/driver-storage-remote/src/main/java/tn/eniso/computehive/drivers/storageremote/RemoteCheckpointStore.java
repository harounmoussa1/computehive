package tn.eniso.computehive.drivers.storageremote;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.drivers.storageremote.RemoteBucket.ObjectInfo;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointKey;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointStorageException;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointStore;
import tn.eniso.computehive.extensions.checkpoints.api.StoredCheckpoint;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Checkpoints sur un stockage objet distant ({@link RemoteBucket} : compatible S3 en déploiement, simulé dans les
 * tests), protégé par Resilience4j ({@link ResilientStorage}, partagé avec les fichiers).
 *
 * <p>Le contenu reçu passe par un fichier temporaire ({@link SpooledContent}) : la mémoire ne dépend pas de sa taille,
 * et chaque nouvel essai renvoie exactement les mêmes octets. Un échec définitif devient une
 * {@link CheckpointStorageException}.</p>
 */
public class RemoteCheckpointStore implements CheckpointStore {

    private static final Logger log = LoggerFactory.getLogger(RemoteCheckpointStore.class);

    private final RemoteBucket bucket;
    private final ResilientStorage resilience;
    private final Clock clock;

    public RemoteCheckpointStore(RemoteBucket bucket, ResilientStorage resilience, Clock clock) {
        this.bucket = bucket;
        this.resilience = resilience;
        this.clock = clock;
    }

    /** Avec sa propre protection : pour tester le store seul. */
    public RemoteCheckpointStore(RemoteBucket bucket, RemoteStorageProperties properties, Clock clock) {
        this(bucket, new ResilientStorage(properties), clock);
    }

    @Override
    public StoredCheckpoint store(CheckpointKey key, InputStream content) {
        try (SpooledContent spooled = SpooledContent.of(content, "computehive-checkpoint-")) {
            ObjectInfo info = new ObjectInfo(spooled.sha256(), spooled.size(), clock.instant());
            call("store " + key, () -> {
                bucket.put(key.path(), spooled.file(), info);
                return null;
            });
            log.info("Checkpoint {} stored remotely ({} bytes, sha256 {})", key, info.sizeBytes(), info.sha256());
            return toStored(key, info);
        } catch (IOException e) {
            throw new CheckpointStorageException("Checkpoint " + key + " could not be received", e);
        }
    }

    @Override
    public Optional<StoredCheckpoint> find(CheckpointKey key) {
        return call("find " + key, () -> bucket.stat(key.path())).map(info -> toStored(key, info));
    }

    @Override
    public InputStream open(CheckpointKey key) {
        return call("open " + key, () -> bucket.open(key.path(), 0))
                .orElseThrow(() -> new NotFoundException("Checkpoint", key.path()));
    }

    /** État du circuit ({@code CLOSED}, {@code OPEN}, {@code HALF_OPEN}), pour la supervision et les tests. */
    public CircuitBreaker.State circuitState() {
        return resilience.circuitState();
    }

    /** Publie l'état du circuit et les résultats des appels (ADR 0014). */
    public void bindTo(MeterRegistry registry) {
        resilience.bindTo(registry);
    }

    private <T> T call(String operation, Supplier<T> call) {
        return resilience.call(operation, call, CheckpointStorageException::new);
    }

    private static StoredCheckpoint toStored(CheckpointKey key, ObjectInfo info) {
        return new StoredCheckpoint(key, info.sha256(), info.sizeBytes(), info.storedAt());
    }
}
