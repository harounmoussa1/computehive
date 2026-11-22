package tn.eniso.computehive.extensions.notifications.api;

import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.core.infra.util.Checks;

import java.util.regex.Pattern;

/**
 * Message à transmettre à un utilisateur. Le contrat ne dit pas comment : c'est le driver qui choisit
 * (logs, e-mail…) et qui sait joindre le destinataire à partir de son identité.
 *
 * @param recipient destinataire (référence étendue : les modules ne connaissent pas les adresses)
 * @param category  catégorie stable, {@code <domaine>.<événement>} en minuscules, ex. {@code "job.done"}
 * @param subject   sujet court
 * @param body      texte du message
 */
public record Notification(UserRef recipient, String category, String subject, String body) {

    private static final Pattern CATEGORY = Pattern.compile("[a-z][a-z0-9]*(\\.[a-z][a-z0-9-]*)+");

    public Notification {
        Checks.requireNonNull(recipient, "recipient");
        Checks.require(category != null && CATEGORY.matcher(category).matches(),
                "category must look like <domain>.<event>, in lowercase: " + category);
        Checks.requireNonBlank(subject, "subject");
        Checks.requireNonNull(body, "body");
    }
}
