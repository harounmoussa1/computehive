package tn.eniso.computehive.drivers.storagelocal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.util.Sha256;
import tn.eniso.computehive.extensions.files.api.FileKey;
import tn.eniso.computehive.extensions.files.api.FileStorageException;
import tn.eniso.computehive.extensions.files.api.FileStore;
import tn.eniso.computehive.extensions.files.api.StoredFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Fichiers des utilisateurs sur le disque du serveur, sous {@code <racine>/files/<sha256>} (en M/1).
 *
 * <ul>
 *   <li>Écriture atomique : le contenu est d'abord écrit dans {@code <racine>/.incoming} pendant le calcul de son
 *       empreinte, puis déplacé d'un coup ; une lecture ne voit jamais un fichier à moitié écrit.</li>
 *   <li>Un fichier de même empreinte déjà présent n'est pas réécrit.</li>
 *   <li>La date du dépôt est celle de la dernière modification du fichier.</li>
 * </ul>
 */
public class LocalFileStore implements FileStore {

    static final String INCOMING = ".incoming";

    private static final Logger log = LoggerFactory.getLogger(LocalFileStore.class);

    private final Path root;
    private final Clock clock;

    public LocalFileStore(Path root, Clock clock) {
        this.root = root.toAbsolutePath().normalize();
        this.clock = clock;
    }

    @Override
    public StoredFile store(InputStream content) {
        Path incoming = null;
        try {
            incoming = Files.createDirectories(root.resolve(INCOMING)).resolve(UUID.randomUUID() + ".part");
            FileKey key;
            try (Sha256.DigestingStream digesting = new Sha256.DigestingStream(content)) {
                Files.copy(digesting, incoming);
                key = new FileKey(digesting.sha256());
            }
            Optional<StoredFile> existing = find(key);
            if (existing.isPresent()) {
                log.info("File {} already stored ({} bytes): not written again", key, existing.get().sizeBytes());
                return existing.get();
            }
            Path target = fileOf(key);
            Files.createDirectories(target.getParent());
            Files.setLastModifiedTime(incoming, FileTime.from(clock.instant()));
            Files.move(incoming, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            StoredFile stored = describe(key, target);
            log.info("File {} stored ({} bytes)", key, stored.sizeBytes());
            return stored;
        } catch (IOException e) {
            throw new FileStorageException("File could not be stored", e);
        } finally {
            deleteQuietly(incoming);
        }
    }

    @Override
    public Optional<StoredFile> find(FileKey key) {
        Path file = fileOf(key);
        try {
            return Optional.of(describe(key, file));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new FileStorageException("File " + key + " could not be read", e);
        }
    }

    @Override
    public InputStream open(FileKey key, long offset) {
        try {
            SeekableByteChannel channel = Files.newByteChannel(fileOf(key));
            channel.position(offset);
            return Channels.newInputStream(channel);
        } catch (NoSuchFileException e) {
            throw new NotFoundException("File", key.sha256());
        } catch (IOException e) {
            throw new FileStorageException("File " + key + " could not be read", e);
        }
    }

    private static StoredFile describe(FileKey key, Path file) throws IOException {
        return new StoredFile(key, Files.size(file), Files.getLastModifiedTime(file).toInstant());
    }

    /** Le format de {@link FileKey} garantit déjà que le chemin reste sous la racine ; on le revérifie. */
    private Path fileOf(FileKey key) {
        Path file = root.resolve(key.path()).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("file key escapes the storage root: " + key);
        }
        return file;
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
