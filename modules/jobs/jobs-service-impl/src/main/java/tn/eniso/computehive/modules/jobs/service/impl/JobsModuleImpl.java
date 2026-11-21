package tn.eniso.computehive.modules.jobs.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.eniso.computehive.core.infra.log.LogKeys;
import tn.eniso.computehive.core.infra.util.Checks;
import tn.eniso.computehive.modules.jobs.infra.Job;
import tn.eniso.computehive.modules.jobs.infra.JobCheckpoint;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobProgress;
import tn.eniso.computehive.modules.jobs.infra.JobQuery;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;
import tn.eniso.computehive.modules.jobs.infra.JobStatusUpdate;
import tn.eniso.computehive.modules.jobs.infra.JobSubmission;
import tn.eniso.computehive.modules.jobs.service.api.JobsModule;
import tn.eniso.computehive.modules.jobs.service.impl.entity.JobCrudService;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Implémentation locale de la façade. Chaque méthode est une transaction du module ;
 * les écritures passent par {@link JobCrudService}, les règles de statut sont dans {@link Job}.
 *
 * <p>Pendant chaque opération sur un job, son identifiant est placé dans le MDC
 * ({@link LogKeys#JOB_ID}) : il apparaît dans tous les logs JSON produits.</p>
 */
@Service
public class JobsModuleImpl implements JobsModule {

    private static final Logger log = LoggerFactory.getLogger(JobsModuleImpl.class);

    private final JobCrudService jobs;
    private final Clock clock;

    public JobsModuleImpl(JobCrudService jobs, Clock clock) {
        this.jobs = jobs;
        this.clock = clock;
    }

    @Override
    @Transactional
    public JobInfo submit(JobSubmission submission) {
        Checks.requireNonNull(submission, "submission");
        String id = UUID.randomUUID().toString();
        return withJobId(id, () -> {
            Job job = jobs.create(Job.submit(id, submission, clock.instant()));
            log.info("Job {} ({}, {}) submitted by {}", id, job.getName(), job.getType(), job.getOwner().id());
            return job.toInfo();
        });
    }

    @Override
    @Transactional
    public JobInfo cancel(String jobId) {
        return withJobId(jobId, () -> jobs.update(jobId, job -> {
            JobStatus before = job.getStatus();
            boolean cancelled = job.cancel(clock.instant());
            if (cancelled) {
                log.info("Job {} cancelled while {}", jobId, before);
            }
            return cancelled;
        }).toInfo());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JobInfo> findById(String jobId) {
        return jobs.findById(jobId).map(Job::toInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobInfo> findByQuery(JobQuery query) {
        Checks.requireNonNull(query, "query");
        return jobs.findByQuery(query).stream().map(Job::toInfo).toList();
    }

    @Override
    @Transactional
    public JobInfo updateStatus(String jobId, JobStatusUpdate update) {
        Checks.requireNonNull(update, "update");
        return withJobId(jobId, () -> jobs.update(jobId, job -> {
            JobStatus before = job.getStatus();
            job.apply(update, clock.instant());
            log.info("Job {} {} -> {}{}", jobId, before, job.getStatus(),
                    job.getMachine() == null ? "" : " on " + job.getMachine().name());
            return true;
        }).toInfo());
    }

    @Override
    @Transactional
    public JobInfo updateProgress(String jobId, JobProgress progress) {
        Checks.requireNonNull(progress, "progress");
        return withJobId(jobId, () -> jobs.update(jobId, job -> {
            job.reportProgress(progress);
            log.debug("Job {} at epoch {}/{} (loss {})", jobId, progress.epoch(), job.getParams().epochs(),
                    progress.loss());
            return true;
        }).toInfo());
    }

    @Override
    @Transactional
    public JobInfo recordCheckpoint(String jobId, JobCheckpoint checkpoint) {
        Checks.requireNonNull(checkpoint, "checkpoint");
        return withJobId(jobId, () -> jobs.update(jobId, job -> {
            boolean recorded = job.recordCheckpoint(checkpoint);
            if (recorded) {
                log.info("Job {} has a new checkpoint at epoch {}", jobId, checkpoint.epoch());
            }
            return recorded;
        }).toInfo());
    }

    /** Exécute {@code action} avec l'identifiant du job dans le MDC, puis rétablit la valeur précédente. */
    private static <T> T withJobId(String jobId, Supplier<T> action) {
        String previous = MDC.get(LogKeys.JOB_ID);
        MDC.put(LogKeys.JOB_ID, jobId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                MDC.remove(LogKeys.JOB_ID);
            } else {
                MDC.put(LogKeys.JOB_ID, previous);
            }
        }
    }
}
