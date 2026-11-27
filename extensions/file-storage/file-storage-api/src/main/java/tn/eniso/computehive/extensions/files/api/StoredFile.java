package tn.eniso.computehive.extensions.files.api;

import tn.eniso.computehive.core.infra.util.Checks;

import java.time.Instant;

/**
 * Fichier enregistré : sa clé (son empreinte), sa taille, la date de son premier dépôt.
 */
public record StoredFile(FileKey key, long sizeBytes, Instant storedAt) {

    public StoredFile {
        Checks.requireNonNull(key, "key");
        Checks.require(sizeBytes >= 0, "sizeBytes must not be negative");
        Checks.requireNonNull(storedAt, "storedAt");
    }

    public String sha256() {
        return key.sha256();
    }
}
