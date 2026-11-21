package tn.eniso.computehive.modules.scheduling.infra.agent;

import tn.eniso.computehive.core.infra.util.Checks;

/**
 * Ordre du serveur à un agent ({@code ServerCommand} du proto). Chaque ordre a un {@code commandId}
 * unique : l'agent n'exécute jamais deux fois le même, ce qui permet de le renvoyer sans risque.
 */
public sealed interface AgentCommand permits AgentCommand.LaunchJob, AgentCommand.StopJob {

    String commandId();

    String jobId();

    long attemptId();

    /**
     * Lancer une tentative ({@code LaunchJob} du proto).
     *
     * @param jobType                type prédéfini, ex. {@code "MNIST_TRAIN"}
     * @param resumeFromEpoch        {@code 0} pour démarrer de zéro
     * @param resumeCheckpointKey    checkpoint à télécharger avant de reprendre, {@code null} sinon
     * @param resumeCheckpointSha256 empreinte attendue de ce checkpoint, {@code null} sinon
     */
    record LaunchJob(String commandId, String jobId, long attemptId, String jobType, int epochs,
                     double learningRate, int batchSize, int resumeFromEpoch, String resumeCheckpointKey,
                     String resumeCheckpointSha256) implements AgentCommand {

        public LaunchJob {
            Checks.requireNonBlank(commandId, "commandId");
            Checks.requireNonBlank(jobId, "jobId");
            Checks.requireNonBlank(jobType, "jobType");
        }
    }

    /**
     * Arrêter une tentative ({@code StopJob} du proto).
     *
     * @param checkpointFirst {@code true} pour sauvegarder un checkpoint avant d'arrêter
     */
    record StopJob(String commandId, String jobId, long attemptId, StopReason reason, boolean checkpointFirst)
            implements AgentCommand {

        public StopJob {
            Checks.requireNonBlank(commandId, "commandId");
            Checks.requireNonBlank(jobId, "jobId");
            Checks.requireNonNull(reason, "reason");
        }
    }

    /** Motif d'un {@code StopJob} ({@code StopReason} du proto). */
    enum StopReason {
        /** L'utilisateur a annulé le job. */
        CANCELLED,
        /** Tentative périmée (fencing). */
        STALE_ATTEMPT,
        /** Le serveur déplace le job. */
        REASSIGNED
    }
}
