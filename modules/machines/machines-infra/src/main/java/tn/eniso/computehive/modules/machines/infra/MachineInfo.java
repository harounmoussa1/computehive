package tn.eniso.computehive.modules.machines.infra;

import tn.eniso.computehive.core.infra.ref.MachineRef;

import java.time.Instant;

/**
 * Vue immuable d'une machine, renvoyée par la façade {@code MachinesModule}.
 * L'entité {@link Machine}, modifiable, ne sort jamais du module (whitepaper §7.1).
 */
public record MachineInfo(
        String id,
        String agentId,
        String name,
        MachineKind kind,
        MachineStatus status,
        String agentVersion,
        int cpuCores,
        long ramTotalMb,
        String gpuName,
        Long gpuMemoryTotalMb,
        Long gpuMemoryFreeMb,
        Integer gpuUtilizationPercent,
        boolean cuda,
        Instant registeredAt,
        Instant lastHeartbeat) {

    public MachineRef toRef() {
        return new MachineRef(id, name, kind.name());
    }
}
