package tn.eniso.computehive.drivers.storageremote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tn.eniso.computehive.core.infra.util.Sha256;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Contenu reçu, recopié dans un fichier temporaire pendant le calcul de son empreinte : la mémoire ne dépend pas de
 * la taille du fichier, et chaque nouvel essai d'envoi relit exactement les mêmes octets. Le fichier est effacé à la
 * fermeture.
 */
final class SpooledContent implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SpooledContent.class);

    private final Path file;
    private final String sha256;
    private final long size;

    private SpooledContent(Path file, String sha256, long size) {
        this.file = file;
        this.sha256 = sha256;
        this.size = size;
    }

    static SpooledContent of(InputStream content, String prefix) throws IOException {
        Path file = Files.createTempFile(prefix, ".part");
        try (Sha256.DigestingStream digesting = new Sha256.DigestingStream(content)) {
            Files.copy(digesting, file, StandardCopyOption.REPLACE_EXISTING);
            return new SpooledContent(file, digesting.sha256(), digesting.size());
        } catch (IOException | RuntimeException e) {
            delete(file);
            throw e;
        }
    }

    Path file() {
        return file;
    }

    String sha256() {
        return sha256;
    }

    long size() {
        return size;
    }

    @Override
    public void close() {
        delete(file);
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Temporary file {} could not be deleted", file, e);
        }
    }
}
