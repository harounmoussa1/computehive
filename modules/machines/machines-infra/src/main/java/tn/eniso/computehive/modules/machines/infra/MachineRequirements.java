package tn.eniso.computehive.modules.machines.infra;

import tn.eniso.computehive.core.infra.util.Checks;

/**
 * Besoins matériels d'un job, utilisés pour chercher une machine compatible.
 *
 * @param gpu             {@code true} si le job a besoin d'un GPU CUDA
 * @param minGpuMemoryMb  mémoire GPU libre minimale en Mo (ignorée si {@code gpu} est faux)
 */
public record MachineRequirements(boolean gpu, long minGpuMemoryMb) {

    public static final MachineRequirements ANY = new MachineRequirements(false, 0);

    public MachineRequirements {
        Checks.require(minGpuMemoryMb >= 0, "minGpuMemoryMb must not be negative");
    }

    public static MachineRequirements gpu(long minGpuMemoryMb) {
        return new MachineRequirements(true, minGpuMemoryMb);
    }
}
