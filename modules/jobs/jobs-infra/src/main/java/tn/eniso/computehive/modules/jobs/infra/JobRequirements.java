package tn.eniso.computehive.modules.jobs.infra;

import tn.eniso.computehive.core.infra.util.Checks;

/**
 * Besoins matériels d'un job. Le module {@code scheduling} les traduit en critères de recherche
 * d'une machine ; {@code jobs} ne connaît pas le module {@code machines}.
 *
 * @param gpu            {@code true} si le job a besoin d'un GPU CUDA
 * @param minGpuMemoryMb mémoire GPU libre minimale en Mo (ignorée si {@code gpu} est faux)
 */
public record JobRequirements(boolean gpu, long minGpuMemoryMb) {

    public static final JobRequirements CPU = new JobRequirements(false, 0);

    public JobRequirements {
        Checks.require(minGpuMemoryMb >= 0, "requirements.minGpuMemoryMb must not be negative");
    }

    public static JobRequirements gpu(long minGpuMemoryMb) {
        return new JobRequirements(true, minGpuMemoryMb);
    }
}
