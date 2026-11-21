package tn.eniso.computehive.modules.machines.service.api;

import tn.eniso.computehive.extensions.broker.api.Topic;
import tn.eniso.computehive.modules.machines.infra.MachineDeadEvent;

/**
 * Événements publiés par le module machines sur l'extension {@code messaging-broker}.
 * Comme la façade, ils font partie du contrat public du module.
 */
public final class MachinesEvents {

    /**
     * Une machine vient de passer à {@code DEAD}. Clé : l'identifiant de la machine.
     * Publié après le commit, donc jamais pour une panne annulée ; livré au moins une fois.
     */
    public static final Topic<MachineDeadEvent> MACHINE_DEAD = Topic.of("machines.machine-dead", MachineDeadEvent.class);

    private MachinesEvents() {
    }
}
