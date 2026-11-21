package tn.eniso.computehive.extensions.broker.api;

/**
 * Contrat de l'extension messaging-broker : publication et abonnement à des événements entre modules.
 * Les modules ne dépendent que de cette interface ; le driver (mémoire en M/1, Kafka en M/P) est
 * choisi à l'assemblage de l'application.
 *
 * <p>Garanties communes à tous les drivers, pour que M/1 et M/P se comportent de la même façon :</p>
 * <ul>
 *   <li><b>asynchrone</b> : {@link #publish} rend la main sans attendre ; les abonnés sont appelés sur
 *       un autre fil d'exécution, donc jamais dans la transaction de celui qui publie ;</li>
 *   <li><b>au moins une fois</b> : un abonné peut recevoir un même événement plusieurs fois
 *       (voir {@link EventHandler}) ;</li>
 *   <li><b>ordre par clé</b> : les événements d'une même clé sont livrés dans l'ordre de publication.</li>
 * </ul>
 *
 * <p>Publier un changement d'état après le commit de la transaction qui l'a produit
 * (intercepteur {@code AFTER_*}) : on n'annonce ainsi jamais un changement annulé.</p>
 */
public interface MessageBroker {

    /**
     * Publie un événement. Sans abonné, il est perdu (driver mémoire) ou conservé selon la
     * rétention du broker (Kafka).
     *
     * @param key clé d'ordre, en général l'identifiant de l'entité concernée (ex. l'id de la machine)
     */
    <E> void publish(Topic<E> topic, String key, E payload);

    /**
     * Abonne {@code handler} au topic. Chaque abonné, désigné par son nom, reçoit chaque événement
     * publié après son abonnement. En M/P, les instances d'une même application qui s'abonnent sous
     * le même nom se partagent les événements (groupe de consommateurs Kafka).
     *
     * @param subscriber nom stable de l'abonné, ex. {@code "scheduling.reassignment"}
     */
    <E> Subscription subscribe(Topic<E> topic, String subscriber, EventHandler<E> handler);
}
