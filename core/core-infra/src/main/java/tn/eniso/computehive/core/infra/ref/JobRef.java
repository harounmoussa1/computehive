package tn.eniso.computehive.core.infra.ref;

import tn.eniso.computehive.core.infra.util.Checks;

/**
 * Référence étendue vers un job appartenant au module {@code jobs}.
 *
 * @param id   identifiant du job
 * @param name nom affiché
 * @param type type de job prédéfini (ex. {@code "MNIST_TRAIN"})
 */
public record JobRef(String id, String name, String type) {

    public JobRef {
        Checks.requireNonBlank(id, "JobRef.id");
    }
}
