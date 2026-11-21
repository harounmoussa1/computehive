package tn.eniso.computehive.modules.scheduling.infra.agent;

import tn.eniso.computehive.core.infra.util.Checks;

import java.util.List;

/**
 * Premier message d'un agent sur chaque nouveau stream ({@code Register} du proto).
 *
 * @param agentId      identifiant stable de l'agent
 * @param name         nom affiché de la machine
 * @param agentVersion version de l'agent
 * @param cpuCores     nombre de cœurs CPU
 * @param ramTotalMb   mémoire vive totale en Mo
 * @param gpu          GPU utilisé, {@code null} pour un worker CPU
 * @param running      jobs que l'agent fait déjà tourner (resynchronisation après une reconnexion)
 */
public record AgentHello(String agentId, String name, String agentVersion, int cpuCores, long ramTotalMb,
                         AgentGpu gpu, List<RunningAttempt> running) {

    public AgentHello {
        Checks.requireNonBlank(agentId, "agentId");
        Checks.requireNonBlank(name, "name");
        running = running == null ? List.of() : List.copyOf(running);
    }

    /**
     * GPU annoncé par l'agent. En v1, une machine utilise un seul GPU : le premier de la liste.
     *
     * @param cuda {@code true} si CUDA est utilisable
     */
    public record AgentGpu(String name, long memoryTotalMb, boolean cuda) {
    }
}
