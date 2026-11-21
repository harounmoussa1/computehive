package tn.eniso.computehive.modules.jobs.service.impl;

import org.springframework.dao.OptimisticLockingFailureException;
import tn.eniso.computehive.modules.jobs.dal.api.JobRepository;
import tn.eniso.computehive.modules.jobs.infra.Job;
import tn.eniso.computehive.modules.jobs.infra.JobQuery;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Faux repository pour les tests unitaires : même contrat que l'implémentation JPA
 * (copies indépendantes, version incrémentée, verrouillage optimiste, tri du plus ancien au plus récent).
 */
class InMemoryJobRepository implements JobRepository {

    private static final Comparator<Job> OLDEST_FIRST =
            Comparator.comparing(Job::getSubmittedAt).thenComparing(Job::getId);

    private final Map<String, Job> rows = new LinkedHashMap<>();

    @Override
    public Job save(Job job) {
        Job stored = rows.get(job.getId());
        if (stored != null && !Objects.equals(stored.getVersion(), job.getVersion())) {
            throw new OptimisticLockingFailureException("stale version of job " + job.getId());
        }
        Job copy = job.copy();
        copy.setVersion(stored == null ? 0L : stored.getVersion() + 1);
        rows.put(copy.getId(), copy);
        return copy.copy();
    }

    @Override
    public Optional<Job> findById(String id) {
        return Optional.ofNullable(rows.get(id)).map(Job::copy);
    }

    @Override
    public List<Job> findByQuery(JobQuery query) {
        return rows.values().stream()
                .filter(job -> query.status() == null || job.getStatus() == query.status())
                .filter(job -> query.ownerId() == null || job.getOwner().id().equals(query.ownerId()))
                .sorted(OLDEST_FIRST)
                .map(Job::copy)
                .toList();
    }
}
