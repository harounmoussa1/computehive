package tn.eniso.computehive.modules.machines.dal.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import tn.eniso.computehive.modules.machines.infra.Machine;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;

import java.time.Instant;

/**
 * Entité de stockage de {@link Machine} (whitepaper §7.3) : table {@code machines.machine}.
 * Elle ne sort jamais de la couche dal-jpa ; l'adaptateur la convertit en entité du domaine.
 */
@Entity
@Table(schema = "machines", name = "machine")
class JpaMachine {

    @Id
    private String id;

    @Column(name = "agent_id", nullable = false, unique = true)
    private String agentId;

    @Column(nullable = false)
    private String name;

    @Column(name = "agent_version")
    private String agentVersion;

    @Column(name = "cpu_cores", nullable = false)
    private int cpuCores;

    @Column(name = "ram_total_mb", nullable = false)
    private long ramTotalMb;

    @Column(name = "gpu_name")
    private String gpuName;

    @Column(name = "gpu_memory_total_mb")
    private Long gpuMemoryTotalMb;

    @Column(name = "gpu_memory_free_mb")
    private Long gpuMemoryFreeMb;

    @Column(name = "gpu_utilization_percent")
    private Integer gpuUtilizationPercent;

    @Column(nullable = false)
    private boolean cuda;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MachineStatus status;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "last_heartbeat", nullable = false)
    private Instant lastHeartbeat;

    @Version
    private Long version;

    protected JpaMachine() {
    }

    static JpaMachine from(Machine machine) {
        JpaMachine entity = new JpaMachine();
        entity.id = machine.getId();
        entity.agentId = machine.getAgentId();
        entity.name = machine.getName();
        entity.agentVersion = machine.getAgentVersion();
        entity.cpuCores = machine.getCpuCores();
        entity.ramTotalMb = machine.getRamTotalMb();
        entity.gpuName = machine.getGpuName();
        entity.gpuMemoryTotalMb = machine.getGpuMemoryTotalMb();
        entity.gpuMemoryFreeMb = machine.getGpuMemoryFreeMb();
        entity.gpuUtilizationPercent = machine.getGpuUtilizationPercent();
        entity.cuda = machine.isCuda();
        entity.status = machine.getStatus();
        entity.registeredAt = machine.getRegisteredAt();
        entity.lastHeartbeat = machine.getLastHeartbeat();
        entity.version = machine.getVersion();
        return entity;
    }

    Machine toDomain() {
        Machine machine = new Machine();
        machine.setId(id);
        machine.setAgentId(agentId);
        machine.setName(name);
        machine.setAgentVersion(agentVersion);
        machine.setCpuCores(cpuCores);
        machine.setRamTotalMb(ramTotalMb);
        machine.setGpuName(gpuName);
        machine.setGpuMemoryTotalMb(gpuMemoryTotalMb);
        machine.setGpuMemoryFreeMb(gpuMemoryFreeMb);
        machine.setGpuUtilizationPercent(gpuUtilizationPercent);
        machine.setCuda(cuda);
        machine.setStatus(status);
        machine.setRegisteredAt(registeredAt);
        machine.setLastHeartbeat(lastHeartbeat);
        machine.setVersion(version);
        return machine;
    }
}
