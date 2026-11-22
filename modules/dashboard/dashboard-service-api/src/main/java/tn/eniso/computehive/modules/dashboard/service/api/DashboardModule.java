package tn.eniso.computehive.modules.dashboard.service.api;

import tn.eniso.computehive.modules.dashboard.infra.DashboardSnapshot;
import tn.eniso.computehive.modules.dashboard.infra.JobDetails;

/**
 * Façade du module dashboard : son unique contrat public.
 *
 * <p>Le module ne possède aucune donnée : il lit les façades de jobs, machines, quotas et scheduling, et compose
 * ce qu'affiche la page. Il ne modifie rien ; soumettre ou annuler un job passe par l'API de jobs.</p>
 */
public interface DashboardModule {

    /** Vue d'ensemble : compteurs, machines, jobs récents, classement des consommations. */
    DashboardSnapshot snapshot();

    /**
     * Un job et l'historique de ses tentatives.
     *
     * @throws tn.eniso.computehive.core.infra.exception.NotFoundException si le job est inconnu
     */
    JobDetails jobDetails(String jobId);
}
