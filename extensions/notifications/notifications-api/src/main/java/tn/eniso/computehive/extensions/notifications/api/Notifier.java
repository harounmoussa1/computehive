package tn.eniso.computehive.extensions.notifications.api;

/**
 * Contrat de l'extension notifications : prévenir un utilisateur. Les modules ne dépendent que de cette
 * interface ; le driver (logs en M/1, e-mail vers Mailpit en démo) est choisi à l'assemblage.
 *
 * <p>Une notification est un service rendu, pas une étape d'une opération : l'appeler depuis un intercepteur
 * {@code AFTER_*}, après le commit. Si elle échoue, l'opération reste valide ; l'échec est seulement journalisé.</p>
 */
public interface Notifier {

    /**
     * Transmet la notification.
     *
     * @throws NotificationException si elle n'a pas pu être transmise (serveur mail injoignable…)
     */
    void notify(Notification notification);
}
