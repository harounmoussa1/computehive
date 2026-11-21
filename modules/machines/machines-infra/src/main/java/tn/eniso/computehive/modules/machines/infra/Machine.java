package tn.eniso.computehive.modules.machines.infra;

import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.util.Checks;

import java.time.Instant;

/**
 * Entité du domaine : un PC worker connu du serveur.
 *
 * <p>Les règles de changement de statut sont portées par l'entité elle-même :</p>
 * <pre>
 *   inscription ──► ONLINE ◄──── release() ──── BUSY
 *                     │  └──────── reserve() ────►│
 *                     │                           │
 *                     └──── markDead() ───► DEAD ◄┘
 *   DEAD ──► ONLINE : nouvelle inscription ou heartbeat reçu
 * </pre>
 *
 * <p>Classe modifiable, visible seulement dans le module ; la façade renvoie {@link MachineInfo}.</p>
 */
public class Machine {

    private String id;
    private String agentId;
    private String name;
    private String agentVersion;
    private int cpuCores;
    private long ramTotalMb;
    private String gpuName;
    private Long gpuMemoryTotalMb;
    private Long gpuMemoryFreeMb;
    private Integer gpuUtilizationPercent;
    private boolean cuda;
    private MachineStatus status;
    private Instant registeredAt;
    private Instant lastHeartbeat;
    private Long version;

    /** Nouvelle machine, {@code ONLINE} dès son inscription. */
    public static Machine register(String id, MachineRegistration registration, Instant now) {
        Machine machine = new Machine();
        machine.id = Checks.requireNonBlank(id, "id");
        machine.registeredAt = now;
        machine.status = MachineStatus.ONLINE;
        machine.applyRegistration(registration, now);
        return machine;
    }

    /**
     * Nouvelle inscription d'une machine déjà connue (reconnexion de l'agent) :
     * met à jour sa description. Une machine {@code DEAD} redevient {@code ONLINE} ;
     * une machine {@code BUSY} le reste, c'est le scheduling qui décide de la libérer.
     */
    public void applyRegistration(MachineRegistration registration, Instant now) {
        Checks.requireNonNull(registration, "registration");
        Checks.require(agentId == null || agentId.equals(registration.agentId()),
                "agentId of a machine cannot change");
        agentId = registration.agentId();
        name = registration.name();
        agentVersion = registration.agentVersion();
        cpuCores = registration.cpuCores();
        ramTotalMb = registration.ramTotalMb();
        GpuSpec gpu = registration.gpu();
        gpuName = gpu == null ? null : gpu.name();
        gpuMemoryTotalMb = gpu == null ? null : gpu.memoryTotalMb();
        gpuMemoryFreeMb = gpuMemoryTotalMb;
        gpuUtilizationPercent = gpu == null ? null : 0;
        cuda = gpu != null && gpu.cuda();
        lastHeartbeat = now;
        if (status == MachineStatus.DEAD) {
            status = MachineStatus.ONLINE;
        }
    }

    /**
     * Enregistre un heartbeat. Une machine {@code DEAD} qui donne de nouveau signe de vie
     * redevient {@code ONLINE} : ses anciens jobs ont déjà été réattribués, et le fencing
     * du scheduling arrêtera ceux qui tourneraient encore.
     */
    public void recordHeartbeat(HeartbeatReport report, Instant now) {
        Checks.requireNonNull(report, "report");
        if (hasGpu()) {
            if (report.gpuMemoryFreeMb() != null) {
                Checks.require(report.gpuMemoryFreeMb() <= gpuMemoryTotalMb,
                        "gpuMemoryFreeMb cannot exceed gpuMemoryTotalMb");
                gpuMemoryFreeMb = report.gpuMemoryFreeMb();
            }
            if (report.gpuUtilizationPercent() != null) {
                gpuUtilizationPercent = report.gpuUtilizationPercent();
            }
        }
        lastHeartbeat = now;
        if (status == MachineStatus.DEAD) {
            status = MachineStatus.ONLINE;
        }
    }

    /** Réserve la machine pour un job : {@code ONLINE → BUSY}. */
    public void reserve() {
        if (status != MachineStatus.ONLINE) {
            throw new ConflictException("Machine " + id + " cannot be reserved while " + status);
        }
        status = MachineStatus.BUSY;
    }

    /** Libère la machine après un job : {@code BUSY → ONLINE}. Sans effet dans les autres états. */
    public void release() {
        if (status == MachineStatus.BUSY) {
            status = MachineStatus.ONLINE;
        }
    }

    /** Déclare la machine en panne. Renvoie {@code false} si elle l'était déjà. */
    public boolean markDead() {
        if (status == MachineStatus.DEAD) {
            return false;
        }
        status = MachineStatus.DEAD;
        return true;
    }

    /** {@code true} si la machine est vivante mais n'a envoyé aucun heartbeat depuis {@code cutoff}. */
    public boolean isLeaseExpired(Instant cutoff) {
        return status != MachineStatus.DEAD && lastHeartbeat.isBefore(cutoff);
    }

    public boolean hasGpu() {
        return gpuName != null;
    }

    public MachineKind kind() {
        return hasGpu() ? MachineKind.GPU : MachineKind.CPU;
    }

    /** Copie indépendante, utilisée comme « état précédent » par les intercepteurs. */
    public Machine copy() {
        Machine copy = new Machine();
        copy.id = id;
        copy.agentId = agentId;
        copy.name = name;
        copy.agentVersion = agentVersion;
        copy.cpuCores = cpuCores;
        copy.ramTotalMb = ramTotalMb;
        copy.gpuName = gpuName;
        copy.gpuMemoryTotalMb = gpuMemoryTotalMb;
        copy.gpuMemoryFreeMb = gpuMemoryFreeMb;
        copy.gpuUtilizationPercent = gpuUtilizationPercent;
        copy.cuda = cuda;
        copy.status = status;
        copy.registeredAt = registeredAt;
        copy.lastHeartbeat = lastHeartbeat;
        copy.version = version;
        return copy;
    }

    public MachineInfo toInfo() {
        return new MachineInfo(id, agentId, name, kind(), status, agentVersion, cpuCores, ramTotalMb,
                gpuName, gpuMemoryTotalMb, gpuMemoryFreeMb, gpuUtilizationPercent, cuda,
                registeredAt, lastHeartbeat);
    }

    // Accesseurs : utilisés par la couche DAL pour lire et reconstruire l'entité.

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }

    public int getCpuCores() {
        return cpuCores;
    }

    public void setCpuCores(int cpuCores) {
        this.cpuCores = cpuCores;
    }

    public long getRamTotalMb() {
        return ramTotalMb;
    }

    public void setRamTotalMb(long ramTotalMb) {
        this.ramTotalMb = ramTotalMb;
    }

    public String getGpuName() {
        return gpuName;
    }

    public void setGpuName(String gpuName) {
        this.gpuName = gpuName;
    }

    public Long getGpuMemoryTotalMb() {
        return gpuMemoryTotalMb;
    }

    public void setGpuMemoryTotalMb(Long gpuMemoryTotalMb) {
        this.gpuMemoryTotalMb = gpuMemoryTotalMb;
    }

    public Long getGpuMemoryFreeMb() {
        return gpuMemoryFreeMb;
    }

    public void setGpuMemoryFreeMb(Long gpuMemoryFreeMb) {
        this.gpuMemoryFreeMb = gpuMemoryFreeMb;
    }

    public Integer getGpuUtilizationPercent() {
        return gpuUtilizationPercent;
    }

    public void setGpuUtilizationPercent(Integer gpuUtilizationPercent) {
        this.gpuUtilizationPercent = gpuUtilizationPercent;
    }

    public boolean isCuda() {
        return cuda;
    }

    public void setCuda(boolean cuda) {
        this.cuda = cuda;
    }

    public MachineStatus getStatus() {
        return status;
    }

    public void setStatus(MachineStatus status) {
        this.status = status;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Instant lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
