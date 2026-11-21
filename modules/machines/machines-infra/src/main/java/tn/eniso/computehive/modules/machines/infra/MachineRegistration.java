package tn.eniso.computehive.modules.machines.infra;

import tn.eniso.computehive.core.infra.util.Checks;

/**
 * Description d'une machine envoyée par l'agent à chaque connexion ({@code Register}).
 *
 * @param agentId      identifiant stable, généré une fois et conservé par l'agent
 * @param name         nom affiché, ex. {@code "labo-pc-12"}
 * @param agentVersion version de l'agent
 * @param cpuCores     nombre de cœurs CPU
 * @param ramTotalMb   mémoire vive totale en Mo
 * @param gpu          GPU utilisé, {@code null} pour un worker CPU
 */
public record MachineRegistration(String agentId, String name, String agentVersion,
                                  int cpuCores, long ramTotalMb, GpuSpec gpu) {

    public MachineRegistration {
        Checks.requireNonBlank(agentId, "agentId");
        Checks.requireNonBlank(name, "name");
        Checks.require(cpuCores >= 0, "cpuCores must not be negative");
        Checks.require(ramTotalMb >= 0, "ramTotalMb must not be negative");
    }
}
