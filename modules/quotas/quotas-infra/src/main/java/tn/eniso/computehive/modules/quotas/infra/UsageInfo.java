package tn.eniso.computehive.modules.quotas.infra;

import tn.eniso.computehive.core.infra.ref.UserRef;

import java.time.Instant;

/**
 * Vue immuable de la consommation d'un utilisateur, renvoyée par la façade {@code QuotasModule}.
 *
 * @param gpuSeconds    temps de calcul consommé par ses jobs GPU terminés
 * @param cpuSeconds    temps de calcul consommé par ses jobs CPU terminés
 * @param jobsCompleted nombre de jobs terminés avec succès
 * @param lastJobAt     fin de son dernier job, {@code null} s'il n'en a terminé aucun
 * @param priority      priorité de placement, de 1 (aucune consommation) vers 0
 */
public record UsageInfo(UserRef user, long gpuSeconds, long cpuSeconds, int jobsCompleted, Instant lastJobAt,
                        double priority) {

    /** Consommation d'un utilisateur qui n'a encore rien terminé. */
    public static UsageInfo none(String userId) {
        return new UsageInfo(new UserRef(userId, null, null), 0, 0, 0, null, UsagePolicy.priority(0, 0));
    }
}
