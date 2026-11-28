package tn.eniso.computehive.drivers.storageremote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.drivers.storageremote.RemoteBucket.ObjectInfo;
import tn.eniso.computehive.extensions.files.api.FileKey;
import tn.eniso.computehive.extensions.files.api.FileStorageException;
import tn.eniso.computehive.extensions.files.api.FileStore;
import tn.eniso.computehive.extensions.files.api.StoredFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Fichiers des utilisateurs (codes, datasets, résultats) sur le stockage objet distant, sous {@code files/<sha256>},
 * protégés par la même politique Resilience4j que les checkpoints ({@link ResilientStorage}).
 *
 * <p>Un fichier déjà présent (même empreinte) n'est pas renvoyé : le dépôt d'un dataset connu est immédiat.</p>
 */
public class RemoteFileStore implements FileStore {

    private static final Logger log = LoggerFactory.getLogger(RemoteFileStore.class);

    private final RemoteBucket bucket;
    private final ResilientStorage resilience;
    private final Clock clock;

    public RemoteFileStore(RemoteBucket bucket, ResilientStorage resilience, Clock clock) {
        this.bucket = bucket;
        this.resilience = resilience;
        this.clock = clock;
    }

    @Override
    public StoredFile store(InputStream content) {
        try (SpooledContent spooled = SpooledContent.of(content, "computehive-file-")) {
            FileKey key = new FileKey(spooled.sha256());
            Optional<StoredFile> existing = find(key);
            if (existing.isPresent()) {
                log.info("File {} already stored ({} bytes): not sent again", key, existing.get().sizeBytes());
                return existing.get();
            }
            ObjectInfo info = new ObjectInfo(spooled.sha256(), spooled.size(), clock.instant());
            call("store " + key, () -> {
                bucket.put(key.path(), spooled.file(), info);
                return null;
            });
            log.info("File {} stored remotely ({} bytes)", key, info.sizeBytes());
            return new StoredFile(key, info.sizeBytes(), info.storedAt());
        } catch (IOException e) {
            throw new FileStorageException("File could not be received", e);
        }
    }

    @Override
    public Optional<StoredFile> find(FileKey key) {
        return call("find " + key, () -> bucket.stat(key.path()))
                .map(info -> new StoredFile(key, info.sizeBytes(), info.storedAt()));
    }

    @Override
    public InputStream open(FileKey key, long offset) {
        return call("open " + key, () -> bucket.open(key.path(), offset))
                .orElseThrow(() -> new NotFoundException("File", key.sha256()));
    }

    private <T> T call(String operation, Supplier<T> call) {
        return resilience.call(operation, call, FileStorageException::new);
    }
}
