package tn.eniso.computehive.modules.scheduling.service.impl.support;

import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.modules.jobs.infra.Job;
import tn.eniso.computehive.modules.jobs.infra.JobCheckpoint;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobProgress;
import tn.eniso.computehive.modules.jobs.infra.JobQuery;
import tn.eniso.computehive.modules.jobs.infra.JobStatusUpdate;
import tn.eniso.computehive.modules.jobs.infra.JobSubmission;
import tn.eniso.computehive.modules.jobs.service.api.JobsModule;

import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Faux module jobs, en mémoire, pour tester scheduling seul. Il s'appuie sur la vraie entité {@link Job} :
 * les règles de statut sont exactement celles du module jobs.
 */
public class FakeJobsModule implements JobsModule {

    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final Clock clock;
    private int sequence;

    public FakeJobsModule(Clock clock) {
        this.clock = clock;
    }

    @Override
    public JobInfo submit(JobSubmission submission) {
        Job job = Job.submit("job-" + (++sequence), submission, clock.instant());
        jobs.put(job.getId(), job);
        return job.toInfo();
    }

    @Override
    public JobInfo cancel(String jobId) {
        Job job = job(jobId);
        job.cancel(clock.instant());
        return job.toInfo();
    }

    @Override
    public Optional<JobInfo> findById(String jobId) {
        return Optional.ofNullable(jobs.get(jobId)).map(Job::toInfo);
    }

    @Override
    public List<JobInfo> findByQuery(JobQuery query) {
        return jobs.values().stream()
                .filter(job -> query.status() == null || job.getStatus() == query.status())
                .filter(job -> query.ownerId() == null || job.getOwner().id().equals(query.ownerId()))
                .sorted(Comparator.comparing(Job::getSubmittedAt).thenComparing(Job::getId))
                .map(Job::toInfo)
                .toList();
    }

    @Override
    public JobInfo updateStatus(String jobId, JobStatusUpdate update) {
        Job job = job(jobId);
        job.apply(update, clock.instant());
        return job.toInfo();
    }

    @Override
    public JobInfo updateProgress(String jobId, JobProgress progress) {
        Job job = job(jobId);
        job.reportProgress(progress);
        return job.toInfo();
    }

    @Override
    public JobInfo recordCheckpoint(String jobId, JobCheckpoint checkpoint) {
        Job job = job(jobId);
        job.recordCheckpoint(checkpoint);
        return job.toInfo();
    }

    public JobInfo get(String jobId) {
        return job(jobId).toInfo();
    }

    private Job job(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            throw new NotFoundException("Job", jobId);
        }
        return job;
    }
}
