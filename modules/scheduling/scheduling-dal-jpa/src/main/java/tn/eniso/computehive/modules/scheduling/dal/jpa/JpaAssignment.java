package tn.eniso.computehive.modules.scheduling.dal.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import tn.eniso.computehive.core.infra.ref.JobRef;
import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.modules.scheduling.infra.Assignment;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentStatus;
import tn.eniso.computehive.modules.scheduling.infra.ResumePoint;

import java.time.Instant;

/**
 * Entité de stockage de {@link Assignment} : table {@code scheduling.assignment}.
 * Elle ne sort jamais de la couche dal-jpa ; l'adaptateur la convertit en entité du domaine.
 */
@Entity
@Table(schema = "scheduling", name = "assignment")
class JpaAssignment {

    @Id
    private String id;

    @Column(name = "job_id", nullable = false)
    private String jobId;

    @Column(name = "job_name")
    private String jobName;

    @Column(name = "job_type")
    private String jobType;

    @Column(name = "machine_id", nullable = false)
    private String machineId;

    @Column(name = "machine_name")
    private String machineName;

    @Column(name = "machine_type")
    private String machineType;

    @Column(name = "attempt_id", nullable = false)
    private long attemptId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentStatus status;

    @Column(name = "resume_epoch", nullable = false)
    private int resumeEpoch;

    @Column(name = "resume_checkpoint_key")
    private String resumeCheckpointKey;

    @Column(name = "resume_checkpoint_sha256")
    private String resumeCheckpointSha256;

    @Column(name = "launch_command_id", nullable = false, unique = true)
    private String launchCommandId;

    @Column(name = "launch_sent_at", nullable = false)
    private Instant launchSentAt;

    @Column(name = "launch_send_count", nullable = false)
    private int launchSendCount;

    @Column(name = "running_since")
    private Instant runningSince;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "end_reason")
    private String endReason;

    @Version
    private Long version;

    protected JpaAssignment() {
    }

    static JpaAssignment from(Assignment assignment) {
        JpaAssignment entity = new JpaAssignment();
        entity.id = assignment.getId();
        entity.jobId = assignment.getJob().id();
        entity.jobName = assignment.getJob().name();
        entity.jobType = assignment.getJob().type();
        entity.machineId = assignment.getMachine().id();
        entity.machineName = assignment.getMachine().name();
        entity.machineType = assignment.getMachine().type();
        entity.attemptId = assignment.getAttemptId();
        entity.status = assignment.getStatus();
        entity.resumeEpoch = assignment.getResumeFrom().epoch();
        entity.resumeCheckpointKey = assignment.getResumeFrom().checkpointKey();
        entity.resumeCheckpointSha256 = assignment.getResumeFrom().checkpointSha256();
        entity.launchCommandId = assignment.getLaunchCommandId();
        entity.launchSentAt = assignment.getLaunchSentAt();
        entity.launchSendCount = assignment.getLaunchSendCount();
        entity.runningSince = assignment.getRunningSince();
        entity.createdAt = assignment.getCreatedAt();
        entity.endedAt = assignment.getEndedAt();
        entity.endReason = assignment.getEndReason();
        entity.version = assignment.getVersion();
        return entity;
    }

    Assignment toDomain() {
        Assignment assignment = new Assignment();
        assignment.setId(id);
        assignment.setJob(new JobRef(jobId, jobName, jobType));
        assignment.setMachine(new MachineRef(machineId, machineName, machineType));
        assignment.setAttemptId(attemptId);
        assignment.setStatus(status);
        assignment.setResumeFrom(new ResumePoint(resumeEpoch, resumeCheckpointKey, resumeCheckpointSha256));
        assignment.setLaunchCommandId(launchCommandId);
        assignment.setLaunchSentAt(launchSentAt);
        assignment.setLaunchSendCount(launchSendCount);
        assignment.setRunningSince(runningSince);
        assignment.setCreatedAt(createdAt);
        assignment.setEndedAt(endedAt);
        assignment.setEndReason(endReason);
        assignment.setVersion(version);
        return assignment;
    }
}
