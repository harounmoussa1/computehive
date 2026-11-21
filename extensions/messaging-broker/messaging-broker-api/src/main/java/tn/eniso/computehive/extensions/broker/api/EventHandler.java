package tn.eniso.computehive.extensions.broker.api;

/**
 * Traitement d'un événement par un abonné.
 *
 * <p>Il doit être <b>idempotent</b> : un même événement ({@link EventEnvelope#eventId()}) peut être
 * livré plus d'une fois. Une exception déclenche de nouvelles tentatives, puis l'événement est
 * journalisé et abandonné ; elle n'arrête jamais la livraison des événements suivants.</p>
 */
@FunctionalInterface
public interface EventHandler<E> {

    void handle(EventEnvelope<E> event);
}
