package tn.eniso.computehive.extensions.checkpoints.api;

import tn.eniso.computehive.core.infra.util.Checks;

import java.time.Instant;

/**
 * Checkpoint enregistré : sa clé, l'empreinte calculée par le stockage pendant l'écriture, sa taille.
 *
 * @param sha256    empreinte SHA-256 du contenu, en hexadécimal minuscule
 * @param sizeBytes taille du contenu
 * @param storedAt  date de l'enregistrement
 */
public record StoredCheckpoint(CheckpointKey key, String sha256, long sizeBytes, Instant storedAt) {

    public StoredCheckpoint {
        Checks.requireNonNull(key, "key");
        Checks.require(sha256 != null && sha256.matches("[0-9a-f]{64}"), "sha256 must be 64 lowercase hex characters");
        Checks.require(sizeBytes >= 0, "sizeBytes must not be negative");
        Checks.requireNonNull(storedAt, "storedAt");
    }
}
