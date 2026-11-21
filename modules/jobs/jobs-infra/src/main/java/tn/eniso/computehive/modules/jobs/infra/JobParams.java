package tn.eniso.computehive.modules.jobs.infra;

import tn.eniso.computehive.core.infra.util.Checks;

/**
 * Hyperparamètres d'entraînement, transmis tels quels à l'agent ({@code JobParams} du proto).
 *
 * @param epochs       nombre d'epochs d'entraînement
 * @param learningRate taux d'apprentissage
 * @param batchSize    taille des lots
 */
public record JobParams(int epochs, double learningRate, int batchSize) {

    public JobParams {
        Checks.require(epochs > 0, "params.epochs must be positive");
        Checks.require(Double.isFinite(learningRate) && learningRate > 0, "params.learningRate must be positive");
        Checks.require(batchSize > 0, "params.batchSize must be positive");
    }
}
