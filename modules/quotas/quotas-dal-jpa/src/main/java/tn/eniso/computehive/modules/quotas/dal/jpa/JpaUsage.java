package tn.eniso.computehive.modules.quotas.dal.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import tn.eniso.computehive.modules.quotas.infra.Usage;

import java.time.Instant;

/**
 * Entité de stockage de {@link Usage} : table {@code quotas.usage}.
 * Elle ne sort jamais de la couche dal-jpa ; l'adaptateur la convertit en entité du domaine.
 */
@Entity
@Table(schema = "quotas", name = "usage")
class JpaUsage {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "user_type")
    private String userType;

    @Column(name = "gpu_seconds", nullable = false)
    private long gpuSeconds;

    @Column(name = "cpu_seconds", nullable = false)
    private long cpuSeconds;

    @Column(name = "jobs_completed", nullable = false)
    private int jobsCompleted;

    @Column(name = "last_job_at")
    private Instant lastJobAt;

    @Version
    private Long version;

    protected JpaUsage() {
    }

    static JpaUsage from(Usage usage) {
        JpaUsage entity = new JpaUsage();
        entity.userId = usage.getUserId();
        entity.userName = usage.getUserName();
        entity.userType = usage.getUserType();
        entity.gpuSeconds = usage.getGpuSeconds();
        entity.cpuSeconds = usage.getCpuSeconds();
        entity.jobsCompleted = usage.getJobsCompleted();
        entity.lastJobAt = usage.getLastJobAt();
        entity.version = usage.getVersion();
        return entity;
    }

    Usage toDomain() {
        Usage usage = new Usage();
        usage.setUserId(userId);
        usage.setUserName(userName);
        usage.setUserType(userType);
        usage.setGpuSeconds(gpuSeconds);
        usage.setCpuSeconds(cpuSeconds);
        usage.setJobsCompleted(jobsCompleted);
        usage.setLastJobAt(lastJobAt);
        usage.setVersion(version);
        return usage;
    }
}
