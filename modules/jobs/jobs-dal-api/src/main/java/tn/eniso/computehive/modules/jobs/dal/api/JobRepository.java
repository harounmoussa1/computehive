package tn.eniso.computehive.modules.jobs.dal.api;

import tn.eniso.computehive.modules.jobs.infra.Job;
import tn.eniso.computehive.modules.jobs.infra.JobQuery;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux jobs. Seul {@code JobCrudService} l'utilise.
 *
 * <p>{@link #save} applique le verrouillage optimiste : enregistrer un job dont la version est
 * périmée lève une {@code OptimisticLockingFailureException}.</p>
 */
public interface JobRepository {

    /** Insère ou met à jour le job, et renvoie l'état enregistré (avec sa nouvelle version). */
    Job save(Job job);

    Optional<Job> findById(String id);

    /** Jobs qui respectent tous les critères non {@code null} de {@code query}, du plus ancien au plus récent. */
    List<Job> findByQuery(JobQuery query);
}
