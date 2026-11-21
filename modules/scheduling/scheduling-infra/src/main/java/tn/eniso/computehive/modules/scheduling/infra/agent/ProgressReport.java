package tn.eniso.computehive.modules.scheduling.infra.agent;

import tn.eniso.computehive.core.infra.util.Checks;

/**
 * Avancement d'une tentative ({@code Progress} du proto, ligne {@code PROGRESS} du script).
 *
 * @param epoch       dernière epoch terminée
 * @param totalEpochs nombre total d'epochs
 * @param loss        perte à la fin de cette epoch
 * @param percent     avancement global, de 0 à 100
 */
public record ProgressReport(String jobId, long attemptId, int epoch, int totalEpochs, double loss, double percent) {

    public ProgressReport {
        Checks.requireNonBlank(jobId, "jobId");
    }
}
