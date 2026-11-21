package tn.eniso.computehive.modules.jobs.infra;

import tn.eniso.computehive.core.infra.util.Checks;

import java.util.regex.Pattern;

/**
 * Checkpoint officiel d'un job : déposé par l'agent, puis vérifié par {@code scheduling}
 * (tentative et SHA-256). C'est depuis lui qu'un job réattribué reprend.
 *
 * @param epoch      epoch après laquelle le checkpoint a été écrit
 * @param storageKey clé du fichier dans l'extension {@code checkpoint-storage}
 * @param sha256     empreinte SHA-256 du fichier, en hexadécimal minuscule
 */
public record JobCheckpoint(int epoch, String storageKey, String sha256) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public JobCheckpoint {
        Checks.require(epoch > 0, "checkpoint.epoch must be positive");
        Checks.requireNonBlank(storageKey, "checkpoint.storageKey");
        Checks.require(sha256 != null && SHA_256.matcher(sha256).matches(),
                "checkpoint.sha256 must be 64 lowercase hexadecimal characters");
    }
}
