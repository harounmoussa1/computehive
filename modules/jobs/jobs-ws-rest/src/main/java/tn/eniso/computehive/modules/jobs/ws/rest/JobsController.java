package tn.eniso.computehive.modules.jobs.ws.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobQuery;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;
import tn.eniso.computehive.modules.jobs.service.api.JobsModule;

import java.net.URI;
import java.util.List;

/**
 * API des utilisateurs : soumettre, annuler et suivre leurs jobs. Traduit HTTP en appels de la
 * façade {@link JobsModule}, sans logique métier ; les erreurs deviennent des réponses
 * Problem Details via {@code ApiExceptionHandler} (400, 404, 409).
 *
 * <p>Identité simulée : en l'absence de module d'authentification, l'utilisateur est désigné par
 * l'en-tête {@value #USER_ID_HEADER} (obligatoire pour soumettre) et {@value #USER_NAME_HEADER}.</p>
 */
@RestController
@RequestMapping(JobsController.BASE_PATH)
public class JobsController {

    static final String BASE_PATH = "/api/jobs";
    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_NAME_HEADER = "X-User-Name";
    static final String USER_TYPE = "MEMBER";

    private final JobsModule jobs;

    public JobsController(JobsModule jobs) {
        this.jobs = jobs;
    }

    /** Soumet un job : {@code 201 Created}, avec son adresse dans l'en-tête {@code Location}. */
    @PostMapping
    public ResponseEntity<JobInfo> submit(@RequestHeader(USER_ID_HEADER) String userId,
                                          @RequestHeader(value = USER_NAME_HEADER, required = false) String userName,
                                          @RequestBody SubmitJobRequest request) {
        JobInfo job = jobs.submit(request.toSubmission(new UserRef(userId, userName, USER_TYPE)));
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + job.id())).body(job);
    }

    /** Annule un job. Le job n'est pas supprimé : il passe à {@code CANCELLED} et reste consultable. */
    @DeleteMapping("/{id}")
    public JobInfo cancel(@PathVariable String id) {
        return jobs.cancel(id);
    }

    @GetMapping("/{id}")
    public JobInfo get(@PathVariable String id) {
        return jobs.findById(id).orElseThrow(() -> new NotFoundException("Job", id));
    }

    /** Liste les jobs, filtrés par statut et par propriétaire si demandés, du plus ancien au plus récent. */
    @GetMapping
    public List<JobInfo> list(@RequestParam(required = false) JobStatus status,
                              @RequestParam(required = false) String owner) {
        return jobs.findByQuery(new JobQuery(status, owner));
    }
}
