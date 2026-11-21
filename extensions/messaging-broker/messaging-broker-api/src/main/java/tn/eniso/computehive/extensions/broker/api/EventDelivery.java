package tn.eniso.computehive.extensions.broker.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import tn.eniso.computehive.core.infra.log.LogKeys;

/**
 * Appel d'un abonné, commun à tous les drivers : le {@code correlationId} de l'événement est placé
 * dans le MDC pendant le traitement (les logs de l'abonné le reprennent), et un échec est retenté
 * avant d'être journalisé.
 */
public final class EventDelivery {

    private static final Logger log = LoggerFactory.getLogger(EventDelivery.class);

    private EventDelivery() {
    }

    /**
     * Livre l'événement à l'abonné, en au plus {@code maxAttempts} tentatives.
     *
     * @return {@code true} si l'abonné l'a traité, {@code false} s'il a échoué à chaque tentative
     */
    public static <E> boolean deliver(EventEnvelope<E> event, String subscriber, EventHandler<E> handler,
                                      int maxAttempts) {
        String previous = MDC.get(LogKeys.CORRELATION_ID);
        if (event.correlationId() != null) {
            MDC.put(LogKeys.CORRELATION_ID, event.correlationId());
        }
        try {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    handler.handle(event);
                    return true;
                } catch (RuntimeException e) {
                    if (attempt == maxAttempts) {
                        log.error("Subscriber {} failed {} times on event {} of {}; event dropped",
                                subscriber, maxAttempts, event.eventId(), event.topic(), e);
                    } else {
                        log.warn("Subscriber {} failed on event {} of {} (attempt {}/{}): {}",
                                subscriber, event.eventId(), event.topic(), attempt, maxAttempts, e.toString());
                    }
                }
            }
            return false;
        } finally {
            if (previous == null) {
                MDC.remove(LogKeys.CORRELATION_ID);
            } else {
                MDC.put(LogKeys.CORRELATION_ID, previous);
            }
        }
    }
}
