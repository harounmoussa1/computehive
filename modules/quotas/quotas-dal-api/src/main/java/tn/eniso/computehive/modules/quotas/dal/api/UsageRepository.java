package tn.eniso.computehive.modules.quotas.dal.api;

import tn.eniso.computehive.modules.quotas.infra.Usage;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux consommations. Seul {@code UsageCrudService} l'utilise.
 *
 * <p>{@link #save} applique le verrouillage optimiste ({@code OptimisticLockingFailureException}) ; créer deux
 * fois la consommation d'un même utilisateur lève une {@code DataIntegrityViolationException}.</p>
 */
public interface UsageRepository {

    Usage save(Usage usage);

    Optional<Usage> findByUserId(String userId);

    /** Toutes les consommations, de la plus forte à la plus faible (temps GPU, puis temps CPU). */
    List<Usage> findAllByConsumption();
}
