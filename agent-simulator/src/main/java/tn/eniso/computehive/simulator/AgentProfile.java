package tn.eniso.computehive.simulator;

import java.util.Objects;

/**
 * Description du PC simulé, envoyée dans {@code Register}.
 *
 * @param agentId     identifiant stable de l'agent
 * @param name        nom affiché, ex. {@code "sim-pc-1"}
 * @param cpuCores    nombre de cœurs CPU
 * @param ramTotalMb  mémoire vive totale en Mo
 * @param gpuName     nom du GPU, {@code null} pour un worker CPU
 * @param gpuMemoryMb mémoire GPU totale en Mo (ignorée sans GPU)
 */
public record AgentProfile(String agentId, String name, int cpuCores, long ramTotalMb, String gpuName,
                           long gpuMemoryMb) {

    public AgentProfile {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(name, "name");
    }

    public static AgentProfile gpu(String agentId, String gpuName, long gpuMemoryMb) {
        return new AgentProfile(agentId, "sim-" + agentId, 8, 32_000, gpuName, gpuMemoryMb);
    }

    public static AgentProfile cpu(String agentId) {
        return new AgentProfile(agentId, "sim-" + agentId, 4, 16_000, null, 0);
    }

    public boolean hasGpu() {
        return gpuName != null;
    }
}
