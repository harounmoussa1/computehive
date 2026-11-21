package tn.eniso.computehive.modules.machines.infra;

import tn.eniso.computehive.core.infra.util.Checks;

/**
 * Mesures envoyées par l'agent à chaque {@code Heartbeat}. Les champs GPU sont {@code null}
 * pour un worker CPU.
 *
 * @param gpuMemoryFreeMb       mémoire GPU libre en Mo
 * @param gpuUtilizationPercent utilisation du GPU, de 0 à 100
 */
public record HeartbeatReport(Long gpuMemoryFreeMb, Integer gpuUtilizationPercent) {

    public static final HeartbeatReport EMPTY = new HeartbeatReport(null, null);

    public HeartbeatReport {
        Checks.require(gpuMemoryFreeMb == null || gpuMemoryFreeMb >= 0, "gpuMemoryFreeMb must not be negative");
        Checks.require(gpuUtilizationPercent == null || (gpuUtilizationPercent >= 0 && gpuUtilizationPercent <= 100),
                "gpuUtilizationPercent must be between 0 and 100");
    }
}
