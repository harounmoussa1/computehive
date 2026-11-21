package tn.eniso.computehive.modules.jobs.dal.jpa;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import tn.eniso.computehive.modules.jobs.dal.api.JobRepository;
import tn.eniso.computehive.modules.jobs.infra.Job;
import tn.eniso.computehive.modules.jobs.infra.JobQuery;

import java.util.List;
import java.util.Optional;

/** Implémentation JPA de {@link JobRepository}. */
@Repository
class JpaJobRepository implements JobRepository {

    /** Du plus ancien au plus récent ; l'identifiant départage deux jobs soumis au même instant. */
    private static final Sort OLDEST_FIRST = Sort.by("submittedAt", "id");

    private final SpringDataJobRepository repository;

    JpaJobRepository(SpringDataJobRepository repository) {
        this.repository = repository;
    }

    /**
     * {@code saveAndFlush} : la version incrémentée par Hibernate n'est connue qu'après l'écriture,
     * et un conflit de version doit être signalé ici plutôt qu'au commit.
     */
    @Override
    public Job save(Job job) {
        return repository.saveAndFlush(JpaJob.from(job)).toDomain();
    }

    @Override
    public Optional<Job> findById(String id) {
        return repository.findById(id).map(JpaJob::toDomain);
    }

    @Override
    public List<Job> findByQuery(JobQuery query) {
        return repository.findAll(matching(query), OLDEST_FIRST).stream().map(JpaJob::toDomain).toList();
    }

    /** Traduit les critères non {@code null} en conditions SQL, reliées par {@code AND}. */
    private static Specification<JpaJob> matching(JobQuery query) {
        Specification<JpaJob> spec = Specification.where(null);
        if (query.status() != null) {
            spec = spec.and((job, q, cb) -> cb.equal(job.get("status"), query.status()));
        }
        if (query.ownerId() != null) {
            spec = spec.and((job, q, cb) -> cb.equal(job.get("ownerId"), query.ownerId()));
        }
        return spec;
    }
}
