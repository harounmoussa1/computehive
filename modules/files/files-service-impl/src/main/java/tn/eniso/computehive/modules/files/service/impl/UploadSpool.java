package tn.eniso.computehive.modules.files.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.extensions.files.api.FileStorageException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Copie d'un dépôt dans un fichier temporaire, le temps de l'inspecter puis de l'envoyer au stockage. La copie
 * s'arrête dès que la taille maximale est dépassée : un envoi trop gros ne remplit pas le disque du serveur.
 * Le fichier est effacé à la fermeture.
 */
final class UploadSpool implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(UploadSpool.class);

    private final Path file;
    private final long size;

    private UploadSpool(Path file, long size) {
        this.file = file;
        this.size = size;
    }

    /** @throws ValidationException si le contenu dépasse {@code maxBytes} */
    static UploadSpool copy(InputStream content, long maxBytes) {
        Path file = null;
        try {
            file = Files.createTempFile("computehive-upload-", ".zip");
            long total = 0;
            try (OutputStream out = Files.newOutputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = content.read(buffer)) > 0) {
                    total += n;
                    if (total > maxBytes) {
                        throw new ValidationException("file too large (more than " + maxBytes + " bytes)");
                    }
                    out.write(buffer, 0, n);
                }
            }
            return new UploadSpool(file, total);
        } catch (IOException e) {
            delete(file);
            throw new FileStorageException("upload could not be received", e);
        } catch (RuntimeException e) {
            delete(file);
            throw e;
        }
    }

    Path file() {
        return file;
    }

    long size() {
        return size;
    }

    @Override
    public void close() {
        delete(file);
    }

    private static void delete(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Temporary upload {} could not be deleted", file, e);
        }
    }
}
