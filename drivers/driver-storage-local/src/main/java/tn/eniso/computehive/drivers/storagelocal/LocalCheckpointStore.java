package tn.eniso.computehive.drivers.storagelocal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointKey;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointStorageException;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointStore;
import tn.eniso.computehive.core.infra.util.Sha256;
import tn.eniso.computehive.extensions.checkpoints.api.StoredCheckpoint;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Checkpoints sur le disque du serveur (en production, un volume Docker), sous {@code <racine>/<clé>}.
 *
 * <ul>
 *   <li>Écriture atomique : le contenu est d'abord écrit dans {@code <racine>/.incoming}, puis déplacé d'un coup ;
 *       une lecture ne voit jamais un fichier à moitié écrit, même si l'agent est coupé pendant l'envoi.</li>
 *   <li>L'empreinte, calculée pendant l'écriture, est gardée à côté ({@code <clé>.sha256}) : la vérification d'un
 *       checkpoint ne relit pas son contenu.</li>
 * </ul>
 */
public class LocalCheckpointStore implements CheckpointStore {

    static final String INCOMING = ".incoming";
    static final String METADATA_SUFFIX = ".sha256";

    private static final Logger log = LoggerFactory.getLogger(LocalCheckpointStore.class);

    private final Path root;
    private final Clock clock;

    public LocalCheckpointStore(Path root, Clock clock) {
        this.root = root.toAbsolutePath().normalize();
        this.clock = clock;
    }

    @Override
    public StoredCheckpoint store(CheckpointKey key, InputStream content) {
        Path target = fileOf(key);
        try {
            Path incoming = Files.createDirectories(root.resolve(INCOMING));
            Path data = incoming.resolve(UUID.randomUUID() + ".part");
            Path metadata = incoming.resolve(UUID.randomUUID() + ".part");
            Sha256.DigestingStream digesting = new Sha256.DigestingStream(content);
            Files.copy(digesting, data);
            StoredCheckpoint stored = new StoredCheckpoint(key, digesting.sha256(), digesting.size(), clock.instant());
            Files.writeString(metadata, stored.sha256() + " " + stored.sizeBytes() + " " + stored.storedAt(),
                    StandardCharsets.UTF_8);
            Files.createDirectories(target.getParent());
            Files.move(data, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            Files.move(metadata, metadataOf(target), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("Checkpoint {} stored ({} bytes, sha256 {})", key, stored.sizeBytes(), stored.sha256());
            return stored;
        } catch (IOException e) {
            throw new CheckpointStorageException("Checkpoint " + key + " could not be stored", e);
        }
    }

    @Override
    public Optional<StoredCheckpoint> find(CheckpointKey key) {
        Path metadata = metadataOf(fileOf(key));
        try {
            String[] fields = Files.readString(metadata, StandardCharsets.UTF_8).trim().split(" ");
            return Optional.of(new StoredCheckpoint(key, fields[0], Long.parseLong(fields[1]), Instant.parse(fields[2])));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            throw new CheckpointStorageException("Metadata of checkpoint " + key + " could not be read", e);
        }
    }

    @Override
    public InputStream open(CheckpointKey key) {
        try {
            return Files.newInputStream(fileOf(key));
        } catch (NoSuchFileException e) {
            throw new NotFoundException("Checkpoint", key.path());
        } catch (IOException e) {
            throw new CheckpointStorageException("Checkpoint " + key + " could not be read", e);
        }
    }

    /** Le format de {@link CheckpointKey} garantit déjà que le chemin reste sous la racine ; on le revérifie. */
    private Path fileOf(CheckpointKey key) {
        Path file = root.resolve(key.path()).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("checkpoint key escapes the storage root: " + key);
        }
        return file;
    }

    private static Path metadataOf(Path file) {
        return file.resolveSibling(file.getFileName() + METADATA_SUFFIX);
    }
}
