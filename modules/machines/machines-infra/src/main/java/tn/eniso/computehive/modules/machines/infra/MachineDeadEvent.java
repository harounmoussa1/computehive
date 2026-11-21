package tn.eniso.computehive.modules.machines.infra;

import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.core.infra.util.Checks;

import java.time.Instant;

/**
 * Événement publié quand une machine passe à {@code DEAD} (topic {@code machines.machine-dead}).
 * {@code scheduling} s'y abonne pour réattribuer les jobs de cette machine.
 *
 * @param machine       machine déclarée en panne
 * @param lastHeartbeat dernier signe de vie reçu
 */
public record MachineDeadEvent(MachineRef machine, Instant lastHeartbeat) {

    public MachineDeadEvent {
        Checks.requireNonNull(machine, "machine");
        Checks.requireNonNull(lastHeartbeat, "lastHeartbeat");
    }
}
