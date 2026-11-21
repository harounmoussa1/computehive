package tn.eniso.computehive.modules.quotas.service.api;

import tn.eniso.computehive.modules.quotas.infra.UsageInfo;

import java.util.List;

/**
 * Façade du module quotas : son unique contrat public.
 *
 * <p>Appelée par {@code scheduling} (ordre de placement des jobs) et par la couche ws-rest (tableau de bord).
 * La consommation est enregistrée par le module lui-même, quand un job passe à {@code DONE}.</p>
 */
public interface QuotasModule {

    /**
     * Priorité de placement des jobs de l'utilisateur, de 1 (aucune consommation) vers 0 :
     * plus il a consommé, plus ses jobs passent tard (voir {@code UsagePolicy}).
     */
    double priorityOf(String userId);

    /** Consommation de l'utilisateur ; nulle s'il n'a encore terminé aucun job. */
    UsageInfo usageOf(String userId);

    /** Toutes les consommations, de la plus forte à la plus faible. */
    List<UsageInfo> ranking();
}
