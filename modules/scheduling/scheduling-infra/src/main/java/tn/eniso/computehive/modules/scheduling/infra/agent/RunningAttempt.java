package tn.eniso.computehive.modules.scheduling.infra.agent;

import tn.eniso.computehive.core.infra.util.Checks;

/**
 * Job qu'un agent déclare faire tourner ({@code RunningJob} du proto), dans {@code Register} et {@code Heartbeat}.
 *
 * @param jobId     job exécuté
 * @param attemptId tentative que l'agent croit exécuter (jeton de fencing)
 */
public record RunningAttempt(String jobId, long attemptId) {

    public RunningAttempt {
        Checks.requireNonBlank(jobId, "jobId");
    }
}
