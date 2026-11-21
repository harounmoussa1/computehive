package tn.eniso.computehive.modules.machines.service.impl;

import org.springframework.stereotype.Component;
import tn.eniso.computehive.modules.machines.infra.Machine;
import tn.eniso.computehive.modules.machines.infra.MachineRequirements;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Choisit, parmi les machines libres, celles qui conviennent à un job, de la plus adaptée à la moins adaptée.
 *
 * <ul>
 *   <li>Job GPU : GPU avec CUDA et assez de mémoire libre ; la plus grande mémoire libre d'abord.</li>
 *   <li>Job CPU : n'importe quelle machine, mais les workers CPU d'abord, pour garder les GPU
 *       aux jobs qui en ont besoin ; puis le plus de cœurs d'abord.</li>
 * </ul>
 * À critères égaux, l'ordre du nom rend le choix reproductible.
 */
@Component
public class MachineSelector {

    private static final Comparator<Machine> FOR_GPU_JOB =
            Comparator.comparing(Machine::getGpuMemoryFreeMb, Comparator.reverseOrder())
                    .thenComparing(Machine::getName);

    private static final Comparator<Machine> FOR_CPU_JOB =
            Comparator.comparing(Machine::hasGpu)
                    .thenComparing(Machine::getCpuCores, Comparator.reverseOrder())
                    .thenComparing(Machine::getName);

    public List<Machine> select(Collection<Machine> freeMachines, MachineRequirements requirements) {
        if (!requirements.gpu()) {
            return freeMachines.stream().sorted(FOR_CPU_JOB).toList();
        }
        return freeMachines.stream()
                .filter(Machine::hasGpu)
                .filter(Machine::isCuda)
                .filter(m -> m.getGpuMemoryFreeMb() >= requirements.minGpuMemoryMb())
                .sorted(FOR_GPU_JOB)
                .toList();
    }
}
