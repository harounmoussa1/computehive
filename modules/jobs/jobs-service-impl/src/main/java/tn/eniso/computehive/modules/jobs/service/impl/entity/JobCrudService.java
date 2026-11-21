package tn.eniso.computehive.modules.jobs.service.impl.entity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorDispatcher;
import tn.eniso.computehive.core.service.api.interceptor.LifecyclePhase;
import tn.eniso.computehive.modules.jobs.dal.api.JobRepository;
import tn.eniso.computehive.modules.jobs.infra.Job;
import tn.eniso.computehive.modules.jobs.infra.JobQuery;

import java.util.List;
import java.util.Optional;

/**
 * Seul propriétaire de {@link JobRepository} dans le module. Toute écriture d'un job passe par ici,
 * et chacune est annoncée aux intercepteurs ({@code BEFORE_*} / {@code AFTER_*}) : c'est ainsi que
 * {@code quotas} apprendra qu'un job est passé à {@code DONE}, sans que {@code jobs} le connaisse.
 */
@Service
public class JobCrudService {

    /** Modification d'un job ; renvoie {@code false} si elle n'a rien changé. */
    @FunctionalInterface
    public interface JobChange {
        boolean apply(Job job);
    }

    private final JobRepository repository;
    private final InterceptorDispatcher interceptors;

    public JobCrudService(JobRepository repository, InterceptorDispatcher interceptors) {
        this.repository = repository;
        this.interceptors = interceptors;
    }

    @Transactional
    public Job create(Job job) {
        interceptors.dispatch(LifecyclePhase.BEFORE_ADD, job, null);
        Job saved = repository.save(job);
        interceptors.dispatch(LifecyclePhase.AFTER_ADD, saved, null);
        return saved;
    }

    /**
     * Charge le job, applique {@code change} puis l'enregistre. Si {@code change} ne modifie
     * rien, rien n'est écrit et aucun intercepteur n'est appelé.
     *
     * @throws NotFoundException si le job n'existe pas
     * @throws org.springframework.dao.OptimisticLockingFailureException s'il a été modifié entre-temps
     */
    @Transactional
    public Job update(String id, JobChange change) {
        Job job = repository.findById(id).orElseThrow(() -> new NotFoundException("Job", id));
        Job previous = job.copy();
        if (!change.apply(job)) {
            return job;
        }
        interceptors.dispatch(LifecyclePhase.BEFORE_UPDATE, job, previous);
        Job saved = repository.save(job);
        interceptors.dispatch(LifecyclePhase.AFTER_UPDATE, saved, previous);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Job> findById(String id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Job> findByQuery(JobQuery query) {
        return repository.findByQuery(query);
    }
}
