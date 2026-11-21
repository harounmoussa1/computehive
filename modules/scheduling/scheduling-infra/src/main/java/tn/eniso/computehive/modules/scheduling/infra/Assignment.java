package tn.eniso.computehive.modules.scheduling.infra;

import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.ref.JobRef;
import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.core.infra.util.Checks;

import java.time.Instant;

/**
 * Entité du domaine : une tentative d'exécution d'un job sur une machine.
 *
 * <p>Le numéro de tentative ({@code attemptId}) est le <b>jeton de fencing</b> : il vaut 1 au premier
 * lancement d'un job et augmente à chaque relance. Seule la tentative active d'un job est écoutée ;
 * un message d'agent qui porte un ancien numéro est rejeté. La base garantit qu'un job et une machine
 * n'ont jamais plus d'une tentative active à la fois.</p>
 *
 * <p>Les règles de changement de statut sont dans {@link AssignmentStatus#canMoveTo}. Classe modifiable,
 * visible seulement dans le module ; la façade renvoie {@link AssignmentInfo}.</p>
 */
public class Assignment {

    /** Longueur maximale conservée de la raison de fin d'une tentative. */
    public static final int MAX_END_REASON_LENGTH = 1000;

    private String id;
    private JobRef job;
    private MachineRef machine;
    private long attemptId;
    private AssignmentStatus status;
    private ResumePoint resumeFrom;
    private String launchCommandId;
    private Instant launchSentAt;
    private int launchSendCount;
    private Instant createdAt;
    private Instant endedAt;
    private String endReason;
    private Long version;

    /**
     * Nouvelle tentative : le {@code LaunchJob} ({@code launchCommandId}) part vers l'agent.
     *
     * @param attemptId numéro de la tentative, à partir de 1
     */
    public static Assignment launch(String id, JobRef job, MachineRef machine, long attemptId, ResumePoint resumeFrom,
                                    String launchCommandId, Instant now) {
        Checks.require(attemptId >= 1, "attemptId must start at 1");
        Assignment assignment = new Assignment();
        assignment.id = Checks.requireNonBlank(id, "id");
        assignment.job = Checks.requireNonNull(job, "job");
        assignment.machine = Checks.requireNonNull(machine, "machine");
        assignment.attemptId = attemptId;
        assignment.resumeFrom = Checks.requireNonNull(resumeFrom, "resumeFrom");
        assignment.launchCommandId = Checks.requireNonBlank(launchCommandId, "launchCommandId");
        assignment.status = AssignmentStatus.LAUNCHING;
        assignment.createdAt = Checks.requireNonNull(now, "now");
        assignment.launchSentAt = now;
        assignment.launchSendCount = 1;
        return assignment;
    }

    /**
     * L'agent exécute la tentative : {@code CommandAck} accepté, ou premier message de progression
     * (si le {@code CommandAck} s'est perdu).
     *
     * @return {@code false} si elle était déjà {@code RUNNING}
     */
    public boolean confirmRunning() {
        if (status == AssignmentStatus.RUNNING) {
            return false;
        }
        moveTo(AssignmentStatus.RUNNING);
        return true;
    }

    /** L'agent a refusé le {@code LaunchJob}. */
    public void reject(String reason, Instant now) {
        end(AssignmentStatus.REJECTED, reason, now);
    }

    /** Le {@code LaunchJob} est renvoyé, avec le même {@code command_id}, faute de {@code CommandAck}. */
    public void recordLaunchResent(Instant now) {
        if (status != AssignmentStatus.LAUNCHING) {
            throw new ConflictException("Launch of assignment " + id + " cannot be resent while " + status);
        }
        launchSentAt = now;
        launchSendCount++;
    }

    public void complete(Instant now) {
        end(AssignmentStatus.COMPLETED, null, now);
    }

    public void fail(String reason, Instant now) {
        end(AssignmentStatus.FAILED, reason, now);
    }

    public void stop(String reason, Instant now) {
        end(AssignmentStatus.STOPPED, reason, now);
    }

    public void lose(String reason, Instant now) {
        end(AssignmentStatus.LOST, reason, now);
    }

    public boolean isActive() {
        return status.isActive();
    }

    private void end(AssignmentStatus terminal, String reason, Instant now) {
        moveTo(terminal);
        endedAt = Checks.requireNonNull(now, "now");
        endReason = reason == null || reason.length() <= MAX_END_REASON_LENGTH
                ? reason
                : reason.substring(0, MAX_END_REASON_LENGTH);
    }

    private void moveTo(AssignmentStatus next) {
        if (!status.canMoveTo(next)) {
            throw new ConflictException("Assignment " + id + " (job " + job.id() + ", attempt " + attemptId
                    + ") cannot go from " + status + " to " + next);
        }
        status = next;
    }

    /** Copie indépendante, utilisée comme « état précédent » par les intercepteurs. */
    public Assignment copy() {
        Assignment copy = new Assignment();
        copy.id = id;
        copy.job = job;
        copy.machine = machine;
        copy.attemptId = attemptId;
        copy.status = status;
        copy.resumeFrom = resumeFrom;
        copy.launchCommandId = launchCommandId;
        copy.launchSentAt = launchSentAt;
        copy.launchSendCount = launchSendCount;
        copy.createdAt = createdAt;
        copy.endedAt = endedAt;
        copy.endReason = endReason;
        copy.version = version;
        return copy;
    }

    public AssignmentInfo toInfo() {
        return new AssignmentInfo(id, job, machine, attemptId, status, resumeFrom, createdAt, endedAt, endReason);
    }

    // Accesseurs : utilisés par la couche DAL pour lire et reconstruire l'entité.

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public JobRef getJob() {
        return job;
    }

    public void setJob(JobRef job) {
        this.job = job;
    }

    public MachineRef getMachine() {
        return machine;
    }

    public void setMachine(MachineRef machine) {
        this.machine = machine;
    }

    public long getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(long attemptId) {
        this.attemptId = attemptId;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public void setStatus(AssignmentStatus status) {
        this.status = status;
    }

    public ResumePoint getResumeFrom() {
        return resumeFrom;
    }

    public void setResumeFrom(ResumePoint resumeFrom) {
        this.resumeFrom = resumeFrom;
    }

    public String getLaunchCommandId() {
        return launchCommandId;
    }

    public void setLaunchCommandId(String launchCommandId) {
        this.launchCommandId = launchCommandId;
    }

    public Instant getLaunchSentAt() {
        return launchSentAt;
    }

    public void setLaunchSentAt(Instant launchSentAt) {
        this.launchSentAt = launchSentAt;
    }

    public int getLaunchSendCount() {
        return launchSendCount;
    }

    public void setLaunchSendCount(int launchSendCount) {
        this.launchSendCount = launchSendCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public String getEndReason() {
        return endReason;
    }

    public void setEndReason(String endReason) {
        this.endReason = endReason;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
