package tn.eniso.computehive.modules.scheduling.infra.agent;

import tn.eniso.computehive.core.infra.util.Checks;

/**
 * Checkpoint déposé par l'agent ({@code CheckpointSaved} du proto), envoyé après le dépôt du fichier.
 *
 * @param epoch      epoch après laquelle le checkpoint a été écrit
 * @param storageKey clé du fichier dans l'extension {@code checkpoint-storage}
 * @param sha256     empreinte calculée par l'agent
 * @param sizeBytes  taille du fichier
 */
public record CheckpointReport(String jobId, long attemptId, int epoch, String storageKey, String sha256,
                               long sizeBytes) {

    public CheckpointReport {
        Checks.requireNonBlank(jobId, "jobId");
    }
}
