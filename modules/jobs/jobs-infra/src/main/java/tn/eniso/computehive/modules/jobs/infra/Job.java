package tn.eniso.computehive.modules.jobs.infra;

import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.core.infra.util.Checks;

import java.time.Instant;

/**
 * Entité du domaine : un job soumis par un utilisateur.
 *
 * <p>Les règles de changement de statut sont portées par {@link JobStatus#canMoveTo} et appliquées
 * ici : toute transition interdite lève une {@link ConflictException} (HTTP 409).</p>
 *
 * <p>Le numéro de tentative ({@code attemptId}) n'est pas ici : il appartient à l'{@code Assignment}
 * du module {@code scheduling}, qui vérifie le fencing avant d'appeler la façade de {@code jobs}.</p>
 *
 * <p>Classe modifiable, visible seulement dans le module ; la façade renvoie {@link JobInfo}.</p>
 */
public class Job {

    private String id;
    private String name;
    private JobType type;
    private UserRef owner;
    private JobParams params;
    private JobRequirements requirements;
    private JobStatus status;
    private MachineRef machine;
    private int currentEpoch;
    private double progress;
    private Double lastLoss;
    private JobCheckpoint lastCheckpoint;
    private Double accuracy;
    private String failureReason;
    private Instant submittedAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Long version;

    /** Nouveau job, {@code PENDING} dès sa soumission. */
    public static Job submit(String id, JobSubmission submission, Instant now) {
        Checks.requireNonNull(submission, "submission");
        Job job = new Job();
        job.id = Checks.requireNonBlank(id, "id");
        job.name = submission.name();
        job.type = submission.type();
        job.owner = submission.owner();
        job.params = submission.params();
        job.requirements = submission.requirements();
        job.status = JobStatus.PENDING;
        job.submittedAt = Checks.requireNonNull(now, "now");
        return job;
    }

    /**
     * Le job démarre sur une machine : {@code PENDING} ou {@code REASSIGNED → RUNNING}.
     * {@code startedAt} garde la date du tout premier démarrage.
     */
    public void start(MachineRef machine, Instant now) {
        Checks.requireNonNull(machine, "machine");
        moveTo(JobStatus.RUNNING);
        this.machine = machine;
        if (startedAt == null) {
            startedAt = now;
        }
    }

    /** Enregistre l'avancement rapporté par l'agent. Seulement pendant {@code RUNNING}. */
    public void reportProgress(JobProgress report) {
        Checks.requireNonNull(report, "report");
        requireRunning("report progress");
        Checks.require(report.epoch() <= params.epochs(), "progress.epoch cannot exceed params.epochs");
        currentEpoch = report.epoch();
        progress = report.percent();
        lastLoss = report.loss();
    }

    /**
     * Enregistre un nouveau checkpoint officiel. Seulement pendant {@code RUNNING}.
     *
     * @return {@code false} si ce checkpoint est déjà le checkpoint officiel (message rejoué)
     * @throws ConflictException si un checkpoint plus récent est déjà officiel
     */
    public boolean recordCheckpoint(JobCheckpoint checkpoint) {
        Checks.requireNonNull(checkpoint, "checkpoint");
        requireRunning("record a checkpoint");
        Checks.require(checkpoint.epoch() <= params.epochs(), "checkpoint.epoch cannot exceed params.epochs");
        if (checkpoint.equals(lastCheckpoint)) {
            return false;
        }
        if (checkpoint.epoch() <= lastCheckpointEpoch()) {
            throw new ConflictException("Job " + id + " already has a checkpoint at epoch " + lastCheckpointEpoch());
        }
        lastCheckpoint = checkpoint;
        return true;
    }

    /**
     * La machine du job est tombée en panne : {@code RUNNING → REASSIGNED}. Le job n'a plus de
     * machine, et son avancement revient au dernier checkpoint, d'où il reprendra.
     */
    public void reassign() {
        moveTo(JobStatus.REASSIGNED);
        machine = null;
        currentEpoch = lastCheckpointEpoch();
        progress = 100.0 * currentEpoch / params.epochs();
    }

    /** Fin réussie : {@code RUNNING → DONE}. */
    public void complete(Double accuracy, Instant now) {
        moveTo(JobStatus.DONE);
        this.accuracy = accuracy;
        currentEpoch = params.epochs();
        progress = 100;
        finishedAt = now;
    }

    /** Échec définitif : {@code RUNNING} ou {@code REASSIGNED → FAILED}. */
    public void fail(String reason, Instant now) {
        moveTo(JobStatus.FAILED);
        failureReason = reason;
        finishedAt = now;
    }

    /**
     * Annulation par l'utilisateur. La machine est conservée : {@code scheduling} sait ainsi
     * à quel agent envoyer le {@code StopJob}.
     *
     * @return {@code false} si le job était déjà annulé
     * @throws ConflictException si le job est déjà terminé autrement
     */
    public boolean cancel(Instant now) {
        if (status == JobStatus.CANCELLED) {
            return false;
        }
        moveTo(JobStatus.CANCELLED);
        finishedAt = now;
        return true;
    }

    /** Epoch du checkpoint officiel, {@code 0} s'il n'y en a pas. */
    public int lastCheckpointEpoch() {
        return lastCheckpoint == null ? 0 : lastCheckpoint.epoch();
    }

    private void moveTo(JobStatus next) {
        if (!status.canMoveTo(next)) {
            throw new ConflictException("Job " + id + " cannot go from " + status + " to " + next);
        }
        status = next;
    }

    private void requireRunning(String action) {
        if (status != JobStatus.RUNNING) {
            throw new ConflictException("Cannot " + action + " for job " + id + " while " + status);
        }
    }

    /** Copie indépendante, utilisée comme « état précédent » par les intercepteurs. */
    public Job copy() {
        Job copy = new Job();
        copy.id = id;
        copy.name = name;
        copy.type = type;
        copy.owner = owner;
        copy.params = params;
        copy.requirements = requirements;
        copy.status = status;
        copy.machine = machine;
        copy.currentEpoch = currentEpoch;
        copy.progress = progress;
        copy.lastLoss = lastLoss;
        copy.lastCheckpoint = lastCheckpoint;
        copy.accuracy = accuracy;
        copy.failureReason = failureReason;
        copy.submittedAt = submittedAt;
        copy.startedAt = startedAt;
        copy.finishedAt = finishedAt;
        copy.version = version;
        return copy;
    }

    public JobInfo toInfo() {
        return new JobInfo(id, name, type, owner, params, requirements, status, machine,
                currentEpoch, progress, lastLoss, lastCheckpoint, accuracy, failureReason,
                submittedAt, startedAt, finishedAt);
    }

    // Accesseurs : utilisés par la couche DAL pour lire et reconstruire l'entité.

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public JobType getType() {
        return type;
    }

    public void setType(JobType type) {
        this.type = type;
    }

    public UserRef getOwner() {
        return owner;
    }

    public void setOwner(UserRef owner) {
        this.owner = owner;
    }

    public JobParams getParams() {
        return params;
    }

    public void setParams(JobParams params) {
        this.params = params;
    }

    public JobRequirements getRequirements() {
        return requirements;
    }

    public void setRequirements(JobRequirements requirements) {
        this.requirements = requirements;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public MachineRef getMachine() {
        return machine;
    }

    public void setMachine(MachineRef machine) {
        this.machine = machine;
    }

    public int getCurrentEpoch() {
        return currentEpoch;
    }

    public void setCurrentEpoch(int currentEpoch) {
        this.currentEpoch = currentEpoch;
    }

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }

    public Double getLastLoss() {
        return lastLoss;
    }

    public void setLastLoss(Double lastLoss) {
        this.lastLoss = lastLoss;
    }

    public JobCheckpoint getLastCheckpoint() {
        return lastCheckpoint;
    }

    public void setLastCheckpoint(JobCheckpoint lastCheckpoint) {
        this.lastCheckpoint = lastCheckpoint;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Double accuracy) {
        this.accuracy = accuracy;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
