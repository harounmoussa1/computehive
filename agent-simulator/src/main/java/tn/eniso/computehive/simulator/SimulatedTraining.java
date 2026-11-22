package tn.eniso.computehive.simulator;

import java.nio.charset.StandardCharsets;

/**
 * Entraînement simulé d'un job, sans PyTorch : une courbe de perte qui décroît avec les epochs, une
 * précision finale, et des checkpoints au contenu fictif.
 * Déterministe : deux exécutions identiques donnent les mêmes valeurs.
 */
final class SimulatedTraining {

    private final String jobId;
    private final long attemptId;
    private final int totalEpochs;
    private final double learningRate;
    private int epoch;
    private int lastCheckpointEpoch;

    /** @param resumeFromEpoch epoch du checkpoint de reprise, 0 pour démarrer de zéro */
    SimulatedTraining(String jobId, long attemptId, int totalEpochs, double learningRate, int resumeFromEpoch) {
        this.jobId = jobId;
        this.attemptId = attemptId;
        this.totalEpochs = totalEpochs;
        this.learningRate = learningRate;
        this.epoch = resumeFromEpoch;
        this.lastCheckpointEpoch = resumeFromEpoch;
    }

    /** Termine l'epoch suivante ; renvoie son numéro. */
    int nextEpoch() {
        epoch++;
        return epoch;
    }

    /** Perte de la dernière epoch : décroît de 2,3 vers 0,05, plus vite avec un taux d'apprentissage élevé. */
    double loss() {
        double speed = 0.25 + Math.min(learningRate, 0.1) * 3;
        return 0.05 + 2.25 * Math.exp(-speed * epoch);
    }

    double percent() {
        return 100.0 * epoch / totalEpochs;
    }

    boolean finished() {
        return epoch >= totalEpochs;
    }

    /** Précision finale, d'autant meilleure que la dernière perte est faible. */
    double accuracy() {
        return Math.round((0.995 - loss() / 10) * 1000) / 1000.0;
    }

    /** Clé du checkpoint de l'epoch courante : elle contient la tentative, une tentative périmée n'écrase donc rien. */
    String checkpointKey() {
        return jobId + "/attempts/" + attemptId + "/epochs/" + epoch;
    }

    /** Contenu fictif du checkpoint de l'epoch courante (environ 16 Ko), différent pour chaque clé. */
    byte[] checkpointContent() {
        String line = "simulated checkpoint " + checkpointKey() + " loss=" + loss() + System.lineSeparator();
        return line.repeat(Math.max(1, 16_384 / line.length())).getBytes(StandardCharsets.UTF_8);
    }

    void recordCheckpoint() {
        lastCheckpointEpoch = epoch;
    }

    String jobId() {
        return jobId;
    }

    long attemptId() {
        return attemptId;
    }

    int epoch() {
        return epoch;
    }

    int totalEpochs() {
        return totalEpochs;
    }

    int lastCheckpointEpoch() {
        return lastCheckpointEpoch;
    }
}
