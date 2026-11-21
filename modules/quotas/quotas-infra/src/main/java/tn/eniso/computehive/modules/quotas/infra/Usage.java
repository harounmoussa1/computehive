package tn.eniso.computehive.modules.quotas.infra;

import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.core.infra.util.Checks;

import java.time.Duration;
import java.time.Instant;

/**
 * Entité du domaine : consommation cumulée d'un utilisateur. L'utilisateur est une référence étendue
 * ({@link UserRef}) : le module ne connaît pas les utilisateurs, il garde une copie de l'identité.
 *
 * <p>Classe modifiable, visible seulement dans le module ; la façade renvoie {@link UsageInfo}.</p>
 */
public class Usage {

    private String userId;
    private String userName;
    private String userType;
    private long gpuSeconds;
    private long cpuSeconds;
    private int jobsCompleted;
    private Instant lastJobAt;
    private Long version;

    /** Première consommation enregistrée pour cet utilisateur. */
    public static Usage startFor(UserRef user) {
        Checks.requireNonNull(user, "user");
        Usage usage = new Usage();
        usage.userId = user.id();
        usage.userName = user.name();
        usage.userType = user.type();
        return usage;
    }

    /**
     * Ajoute un job terminé. Le nom de l'utilisateur est rafraîchi avec celui du job, plus récent.
     *
     * @param duration temps de calcul du job (de son premier démarrage à sa fin)
     * @param gpu      {@code true} si c'était un job GPU
     */
    public void addCompletedJob(UserRef user, Duration duration, boolean gpu, Instant finishedAt) {
        Checks.requireNonNull(user, "user");
        Checks.require(user.id().equals(userId), "usage of " + userId + " cannot record a job of " + user.id());
        Checks.require(duration != null && !duration.isNegative(), "job duration must not be negative");
        if (gpu) {
            gpuSeconds += duration.toSeconds();
        } else {
            cpuSeconds += duration.toSeconds();
        }
        jobsCompleted++;
        if (user.name() != null) {
            userName = user.name();
            userType = user.type();
        }
        if (lastJobAt == null || finishedAt.isAfter(lastJobAt)) {
            lastJobAt = finishedAt;
        }
    }

    public double priority() {
        return UsagePolicy.priority(gpuSeconds, cpuSeconds);
    }

    public Usage copy() {
        Usage copy = new Usage();
        copy.userId = userId;
        copy.userName = userName;
        copy.userType = userType;
        copy.gpuSeconds = gpuSeconds;
        copy.cpuSeconds = cpuSeconds;
        copy.jobsCompleted = jobsCompleted;
        copy.lastJobAt = lastJobAt;
        copy.version = version;
        return copy;
    }

    public UsageInfo toInfo() {
        return new UsageInfo(new UserRef(userId, userName, userType), gpuSeconds, cpuSeconds, jobsCompleted,
                lastJobAt, priority());
    }

    // Accesseurs : utilisés par la couche DAL pour lire et reconstruire l'entité.

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public long getGpuSeconds() {
        return gpuSeconds;
    }

    public void setGpuSeconds(long gpuSeconds) {
        this.gpuSeconds = gpuSeconds;
    }

    public long getCpuSeconds() {
        return cpuSeconds;
    }

    public void setCpuSeconds(long cpuSeconds) {
        this.cpuSeconds = cpuSeconds;
    }

    public int getJobsCompleted() {
        return jobsCompleted;
    }

    public void setJobsCompleted(int jobsCompleted) {
        this.jobsCompleted = jobsCompleted;
    }

    public Instant getLastJobAt() {
        return lastJobAt;
    }

    public void setLastJobAt(Instant lastJobAt) {
        this.lastJobAt = lastJobAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
