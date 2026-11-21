package tn.eniso.computehive.modules.scheduling.infra.agent;

import tn.eniso.computehive.core.infra.util.Checks;

/**
 * Fin d'une tentative sur l'agent ({@code Finished} du proto).
 *
 * @param outcome             issue de la tentative
 * @param accuracy            précision finale, si {@code SUCCEEDED}
 * @param error               cause de l'échec, si {@code FAILED}
 * @param lastCheckpointEpoch epoch du dernier checkpoint écrit par l'agent
 */
public record FinishedReport(String jobId, long attemptId, Outcome outcome, Double accuracy, String error,
                             int lastCheckpointEpoch) {

    public FinishedReport {
        Checks.requireNonBlank(jobId, "jobId");
        Checks.requireNonNull(outcome, "outcome");
    }

    public enum Outcome {
        /** Entraînement terminé avec succès. */
        SUCCEEDED,
        /** Le script a échoué, y compris après les relances locales de l'agent. */
        FAILED,
        /** Arrêté suite à un {@code StopJob} du serveur. */
        STOPPED
    }
}
