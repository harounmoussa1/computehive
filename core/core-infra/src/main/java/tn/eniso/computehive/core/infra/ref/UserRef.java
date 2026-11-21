package tn.eniso.computehive.core.infra.ref;

import tn.eniso.computehive.core.infra.util.Checks;

/**
 * Référence étendue vers un utilisateur : copie légère stockée dans les tables du module
 * qui la détient, pour afficher et filtrer sans appeler le module propriétaire.
 *
 * @param id   identifiant de l'utilisateur
 * @param name nom affiché
 * @param type discriminant (ex. {@code "MEMBER"}, {@code "ADMIN"})
 */
public record UserRef(String id, String name, String type) {

    public UserRef {
        Checks.requireNonBlank(id, "UserRef.id");
    }
}
