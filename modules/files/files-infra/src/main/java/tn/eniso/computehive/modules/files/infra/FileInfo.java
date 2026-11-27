package tn.eniso.computehive.modules.files.infra;

import tn.eniso.computehive.core.infra.ref.UserRef;

import java.time.Instant;
import java.util.List;

/**
 * Vue immuable d'un fichier de la bibliothèque, renvoyée par la façade {@code FilesModule}.
 * L'entité {@link UserFile}, modifiable, ne sort jamais du module.
 *
 * @param sha256 empreinte du contenu : c'est aussi sa clé de stockage ({@code GET /api/files/{sha256}})
 */
public record FileInfo(
        String id,
        UserRef owner,
        FileKind kind,
        String name,
        String sha256,
        long sizeBytes,
        int entryCount,
        long uncompressedBytes,
        List<String> entries,
        Instant uploadedAt) {
}
