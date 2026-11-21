package tn.eniso.computehive.modules.jobs.infra;

import tn.eniso.computehive.core.infra.util.Checks;

/**
 * Avancement rapporté par l'agent ({@code Progress} du proto, ligne {@code PROGRESS} du script).
 *
 * @param epoch   dernière epoch terminée
 * @param loss    perte à la fin de cette epoch
 * @param percent avancement global, de 0 à 100
 */
public record JobProgress(int epoch, double loss, double percent) {

    public JobProgress {
        Checks.require(epoch >= 0, "progress.epoch must not be negative");
        Checks.require(Double.isFinite(loss), "progress.loss must be a finite number");
        Checks.require(percent >= 0 && percent <= 100, "progress.percent must be between 0 and 100");
    }
}
