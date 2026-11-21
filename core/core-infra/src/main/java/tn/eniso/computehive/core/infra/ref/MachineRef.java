package tn.eniso.computehive.core.infra.ref;

import tn.eniso.computehive.core.infra.util.Checks;

/**
 * Référence étendue vers une machine (worker) appartenant au module {@code machines}.
 *
 * @param id   identifiant de la machine
 * @param name nom affiché (ex. {@code "labo-pc-12"})
 * @param type discriminant : {@code "GPU"} ou {@code "CPU"}
 */
public record MachineRef(String id, String name, String type) {

    public MachineRef {
        Checks.requireNonBlank(id, "MachineRef.id");
    }
}
