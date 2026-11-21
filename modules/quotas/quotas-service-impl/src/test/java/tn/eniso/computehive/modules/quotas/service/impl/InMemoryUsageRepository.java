package tn.eniso.computehive.modules.quotas.service.impl;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import tn.eniso.computehive.modules.quotas.dal.api.UsageRepository;
import tn.eniso.computehive.modules.quotas.infra.Usage;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Faux repository pour les tests unitaires : même contrat que l'implémentation JPA. Il peut simuler des
 * conflits de version, comme quand deux jobs d'un même utilisateur se terminent en même temps.
 */
class InMemoryUsageRepository implements UsageRepository {

    private final Map<String, Usage> rows = new LinkedHashMap<>();
    private int conflictsToSimulate;

    /** Les {@code count} prochains enregistrements échouent par conflit de version. */
    void simulateConflicts(int count) {
        conflictsToSimulate = count;
    }

    @Override
    public Usage save(Usage usage) {
        if (conflictsToSimulate > 0) {
            conflictsToSimulate--;
            throw new OptimisticLockingFailureException("simulated concurrent update of " + usage.getUserId());
        }
        Usage stored = rows.get(usage.getUserId());
        if (stored != null && usage.getVersion() == null) {
            throw new DataIntegrityViolationException("usage of " + usage.getUserId() + " already exists");
        }
        if (stored != null && !Objects.equals(stored.getVersion(), usage.getVersion())) {
            throw new OptimisticLockingFailureException("stale version of usage " + usage.getUserId());
        }
        Usage copy = usage.copy();
        copy.setVersion(stored == null ? 0L : stored.getVersion() + 1);
        rows.put(copy.getUserId(), copy);
        return copy.copy();
    }

    @Override
    public Optional<Usage> findByUserId(String userId) {
        return Optional.ofNullable(rows.get(userId)).map(Usage::copy);
    }

    @Override
    public List<Usage> findAllByConsumption() {
        return rows.values().stream()
                .sorted(Comparator.comparingLong(Usage::getGpuSeconds).reversed()
                        .thenComparing(Comparator.comparingLong(Usage::getCpuSeconds).reversed()))
                .map(Usage::copy)
                .toList();
    }
}
