package tn.eniso.computehive.modules.dashboard.infra;

import tn.eniso.computehive.core.infra.util.Checks;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentInfo;

import java.util.List;

/**
 * Détail d'un job pour le tableau de bord : le job et l'historique de ses tentatives (une par machine essayée).
 *
 * @param attempts          tentatives, de la première à la dernière ; vide si elles sont indisponibles
 * @param attemptsAvailable {@code false} si scheduling n'a pas pu répondre (en M/P, {@code app-scheduling}
 *                          injoignable) : le job s'affiche quand même, sans son historique
 */
public record JobDetails(JobInfo job, List<AssignmentInfo> attempts, boolean attemptsAvailable) {

    public JobDetails {
        Checks.requireNonNull(job, "job");
        attempts = List.copyOf(attempts);
    }
}
