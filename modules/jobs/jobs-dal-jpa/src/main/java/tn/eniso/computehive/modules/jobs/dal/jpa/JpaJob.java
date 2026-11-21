package tn.eniso.computehive.modules.jobs.dal.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.modules.jobs.infra.Job;
import tn.eniso.computehive.modules.jobs.infra.JobCheckpoint;
import tn.eniso.computehive.modules.jobs.infra.JobParams;
import tn.eniso.computehive.modules.jobs.infra.JobRequirements;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;
import tn.eniso.computehive.modules.jobs.infra.JobType;

import java.time.Instant;

/**
 * Entité de stockage de {@link Job} (whitepaper §7.3) : table {@code jobs.job}.
 * Elle ne sort jamais de la couche dal-jpa ; l'adaptateur la convertit en entité du domaine.
 *
 * <p>Les objets valeur du domaine ({@code UserRef}, {@code JobParams}, {@code MachineRef}…) sont
 * aplatis en colonnes : la table reste lisible en SQL et dans pgAdmin.</p>
 */
@Entity
@Table(schema = "jobs", name = "job")
class JpaJob {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobType type;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "owner_name")
    private String ownerName;

    @Column(name = "owner_type")
    private String ownerType;

    @Column(nullable = false)
    private int epochs;

    @Column(name = "learning_rate", nullable = false)
    private double learningRate;

    @Column(name = "batch_size", nullable = false)
    private int batchSize;

    @Column(name = "requires_gpu", nullable = false)
    private boolean requiresGpu;

    @Column(name = "min_gpu_memory_mb", nullable = false)
    private long minGpuMemoryMb;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(name = "machine_id")
    private String machineId;

    @Column(name = "machine_name")
    private String machineName;

    @Column(name = "machine_type")
    private String machineType;

    @Column(name = "current_epoch", nullable = false)
    private int currentEpoch;

    @Column(nullable = false)
    private double progress;

    @Column(name = "last_loss")
    private Double lastLoss;

    @Column(name = "checkpoint_epoch")
    private Integer checkpointEpoch;

    @Column(name = "checkpoint_storage_key")
    private String checkpointStorageKey;

    @Column(name = "checkpoint_sha256")
    private String checkpointSha256;

    private Double accuracy;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Version
    private Long version;

    protected JpaJob() {
    }

    static JpaJob from(Job job) {
        JpaJob entity = new JpaJob();
        entity.id = job.getId();
        entity.name = job.getName();
        entity.type = job.getType();
        entity.ownerId = job.getOwner().id();
        entity.ownerName = job.getOwner().name();
        entity.ownerType = job.getOwner().type();
        entity.epochs = job.getParams().epochs();
        entity.learningRate = job.getParams().learningRate();
        entity.batchSize = job.getParams().batchSize();
        entity.requiresGpu = job.getRequirements().gpu();
        entity.minGpuMemoryMb = job.getRequirements().minGpuMemoryMb();
        entity.status = job.getStatus();
        MachineRef machine = job.getMachine();
        entity.machineId = machine == null ? null : machine.id();
        entity.machineName = machine == null ? null : machine.name();
        entity.machineType = machine == null ? null : machine.type();
        entity.currentEpoch = job.getCurrentEpoch();
        entity.progress = job.getProgress();
        entity.lastLoss = job.getLastLoss();
        JobCheckpoint checkpoint = job.getLastCheckpoint();
        entity.checkpointEpoch = checkpoint == null ? null : checkpoint.epoch();
        entity.checkpointStorageKey = checkpoint == null ? null : checkpoint.storageKey();
        entity.checkpointSha256 = checkpoint == null ? null : checkpoint.sha256();
        entity.accuracy = job.getAccuracy();
        entity.failureReason = job.getFailureReason();
        entity.submittedAt = job.getSubmittedAt();
        entity.startedAt = job.getStartedAt();
        entity.finishedAt = job.getFinishedAt();
        entity.version = job.getVersion();
        return entity;
    }

    Job toDomain() {
        Job job = new Job();
        job.setId(id);
        job.setName(name);
        job.setType(type);
        job.setOwner(new UserRef(ownerId, ownerName, ownerType));
        job.setParams(new JobParams(epochs, learningRate, batchSize));
        job.setRequirements(new JobRequirements(requiresGpu, minGpuMemoryMb));
        job.setStatus(status);
        job.setMachine(machineId == null ? null : new MachineRef(machineId, machineName, machineType));
        job.setCurrentEpoch(currentEpoch);
        job.setProgress(progress);
        job.setLastLoss(lastLoss);
        job.setLastCheckpoint(checkpointEpoch == null ? null
                : new JobCheckpoint(checkpointEpoch, checkpointStorageKey, checkpointSha256));
        job.setAccuracy(accuracy);
        job.setFailureReason(failureReason);
        job.setSubmittedAt(submittedAt);
        job.setStartedAt(startedAt);
        job.setFinishedAt(finishedAt);
        job.setVersion(version);
        return job;
    }
}
