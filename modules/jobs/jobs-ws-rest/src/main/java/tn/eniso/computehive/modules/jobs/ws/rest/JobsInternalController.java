package tn.eniso.computehive.modules.jobs.ws.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.eniso.computehive.modules.jobs.infra.JobCheckpoint;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobProgress;
import tn.eniso.computehive.modules.jobs.infra.JobQuery;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;
import tn.eniso.computehive.modules.jobs.infra.JobStatusUpdate;
import tn.eniso.computehive.modules.jobs.infra.JobSubmission;
import tn.eniso.computehive.modules.jobs.service.api.JobsModule;

import java.util.List;

/**
 * API interne (mode M/P) : la façade {@link JobsModule} exposée telle quelle à son proxy
 * {@code jobs-service-rest-cli}, utilisé par {@code app-scheduling}. Une méthode de la façade = un endpoint, sans
 * logique ; les erreurs métier deviennent 404/409/400 ({@code ApiExceptionHandler}) et le proxy les relève à
 * l'identique. Réservée aux applications : {@code InternalApiFilter} exige le jeton interne.
 *
 * <p>Un résultat {@code Optional} vide est renvoyé en {@code 204 No Content}, pour ne pas le confondre avec une
 * erreur 404.</p>
 */
@RestController
@RequestMapping("/internal/jobs")
public class JobsInternalController {

    private final JobsModule jobs;

    public JobsInternalController(JobsModule jobs) {
        this.jobs = jobs;
    }

    @PostMapping
    public JobInfo submit(@RequestBody JobSubmission submission) {
        return jobs.submit(submission);
    }

    @PostMapping("/{id}/cancel")
    public JobInfo cancel(@PathVariable String id) {
        return jobs.cancel(id);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobInfo> findById(@PathVariable String id) {
        return jobs.findById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping
    public List<JobInfo> findByQuery(@RequestParam(required = false) JobStatus status,
                                     @RequestParam(required = false) String ownerId) {
        return jobs.findByQuery(new JobQuery(status, ownerId));
    }

    @PostMapping("/{id}/status")
    public JobInfo updateStatus(@PathVariable String id, @RequestBody JobStatusUpdate update) {
        return jobs.updateStatus(id, update);
    }

    @PostMapping("/{id}/progress")
    public JobInfo updateProgress(@PathVariable String id, @RequestBody JobProgress progress) {
        return jobs.updateProgress(id, progress);
    }

    @PostMapping("/{id}/checkpoint")
    public JobInfo recordCheckpoint(@PathVariable String id, @RequestBody JobCheckpoint checkpoint) {
        return jobs.recordCheckpoint(id, checkpoint);
    }
}
