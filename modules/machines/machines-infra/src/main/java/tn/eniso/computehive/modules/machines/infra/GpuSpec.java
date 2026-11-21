package tn.eniso.computehive.modules.machines.infra;

import tn.eniso.computehive.core.infra.util.Checks;

/**
 * GPU annoncé par l'agent à l'inscription. En v1, une machine utilise un seul GPU :
 * le premier de la liste envoyée par l'agent.
 *
 * @param name          ex. {@code "NVIDIA GeForce RTX 3060"}
 * @param memoryTotalMb mémoire totale en Mo
 * @param cuda          {@code true} si CUDA est utilisable
 */
public record GpuSpec(String name, long memoryTotalMb, boolean cuda) {

    public GpuSpec {
        Checks.requireNonBlank(name, "gpu.name");
        Checks.require(memoryTotalMb > 0, "gpu.memoryTotalMb must be positive");
    }
}
