package tn.eniso.computehive.modules.quotas.dal.jpa;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import tn.eniso.computehive.modules.quotas.dal.api.UsageRepository;
import tn.eniso.computehive.modules.quotas.infra.Usage;

import java.util.List;
import java.util.Optional;

/** Implémentation JPA de {@link UsageRepository}. */
@Repository
class JpaUsageRepository implements UsageRepository {

    private static final Sort BY_CONSUMPTION = Sort.by(Sort.Order.desc("gpuSeconds"), Sort.Order.desc("cpuSeconds"),
            Sort.Order.asc("userId"));

    private final SpringDataUsageRepository repository;

    JpaUsageRepository(SpringDataUsageRepository repository) {
        this.repository = repository;
    }

    /**
     * {@code saveAndFlush} : la version incrémentée n'est connue qu'après l'écriture. Une consommation nouvelle
     * (sans version) dont l'utilisateur existe déjà est refusée plutôt que fusionnée en silence.
     */
    @Override
    public Usage save(Usage usage) {
        if (usage.getVersion() == null && repository.existsById(usage.getUserId())) {
            throw new DataIntegrityViolationException("usage of " + usage.getUserId() + " already exists");
        }
        return repository.saveAndFlush(JpaUsage.from(usage)).toDomain();
    }

    @Override
    public Optional<Usage> findByUserId(String userId) {
        return repository.findById(userId).map(JpaUsage::toDomain);
    }

    @Override
    public List<Usage> findAllByConsumption() {
        return repository.findAll(BY_CONSUMPTION).stream().map(JpaUsage::toDomain).toList();
    }
}
