package tn.eniso.computehive.modules.jobs.service.restcli;

import org.springframework.core.ParameterizedTypeReference;
import tn.eniso.computehive.core.service.restcli.RemoteModuleClient;
import tn.eniso.computehive.modules.jobs.infra.JobCheckpoint;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobProgress;
import tn.eniso.computehive.modules.jobs.infra.JobQuery;
import tn.eniso.computehive.modules.jobs.infra.JobStatusUpdate;
import tn.eniso.computehive.modules.jobs.infra.JobSubmission;
import tn.eniso.computehive.modules.jobs.service.api.JobsModule;

import java.util.List;
import java.util.Optional;

/**
 * Proxy de {@link JobsModule} quand le module jobs tourne dans une autre application (mode M/P) : chaque méthode
 * appelle l'endpoint interne correspondant ({@code JobsInternalController}). Les erreurs métier du module distant
 * sont relevées à l'identique ; une panne devient
 * {@link tn.eniso.computehive.core.infra.exception.RemoteModuleUnavailableException}.
 */
public class JobsModuleRestClient implements JobsModule {

    private static final ParameterizedTypeReference<List<JobInfo>> JOB_LIST = new ParameterizedTypeReference<>() {
    };

    private final RemoteModuleClient remote;

    public JobsModuleRestClient(RemoteModuleClient remote) {
        this.remote = remote;
    }

    @Override
    public JobInfo submit(JobSubmission submission) {
        return remote.command(rest -> rest.post().uri("/internal/jobs").body(submission)
                .retrieve().body(JobInfo.class));
    }

    @Override
    public JobInfo cancel(String jobId) {
        return remote.command(rest -> rest.post().uri("/internal/jobs/{id}/cancel", jobId)
                .retrieve().body(JobInfo.class));
    }

    @Override
    public Optional<JobInfo> findById(String jobId) {
        return remote.query(rest -> Optional.ofNullable(rest.get().uri("/internal/jobs/{id}", jobId)
                .retrieve().body(JobInfo.class)));
    }

    @Override
    public List<JobInfo> findByQuery(JobQuery query) {
        return remote.query(rest -> rest.get()
                .uri(uri -> uri.path("/internal/jobs")
                        .queryParamIfPresent("status", Optional.ofNullable(query.status()))
                        .queryParamIfPresent("ownerId", Optional.ofNullable(query.ownerId()))
                        .build())
                .retrieve().body(JOB_LIST));
    }

    @Override
    public JobInfo updateStatus(String jobId, JobStatusUpdate update) {
        return remote.command(rest -> rest.post().uri("/internal/jobs/{id}/status", jobId).body(update)
                .retrieve().body(JobInfo.class));
    }

    @Override
    public JobInfo updateProgress(String jobId, JobProgress progress) {
        return remote.command(rest -> rest.post().uri("/internal/jobs/{id}/progress", jobId).body(progress)
                .retrieve().body(JobInfo.class));
    }

    @Override
    public JobInfo recordCheckpoint(String jobId, JobCheckpoint checkpoint) {
        return remote.command(rest -> rest.post().uri("/internal/jobs/{id}/checkpoint", jobId).body(checkpoint)
                .retrieve().body(JobInfo.class));
    }
}
