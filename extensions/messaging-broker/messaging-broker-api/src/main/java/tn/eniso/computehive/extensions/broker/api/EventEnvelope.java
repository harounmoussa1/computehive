package tn.eniso.computehive.extensions.broker.api;

import org.slf4j.MDC;
import tn.eniso.computehive.core.infra.log.LogKeys;
import tn.eniso.computehive.core.infra.util.Checks;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Événement tel qu'il est livré à un abonné : le contenu publié, accompagné de ses métadonnées.
 *
 * @param eventId       identifiant unique ; la livraison étant « au moins une fois », l'abonné s'en sert
 *                      pour ignorer un doublon
 * @param topic         nom du topic
 * @param key           clé d'ordre : les événements d'une même clé sont livrés dans l'ordre de publication
 * @param occurredAt    date de publication
 * @param correlationId identifiant de la requête d'origine, repris dans les logs de l'abonné ({@code null} si aucun)
 * @param payload       contenu publié
 */
public record EventEnvelope<E>(String eventId, String topic, String key, Instant occurredAt,
                               String correlationId, E payload) {

    public EventEnvelope {
        Checks.requireNonBlank(eventId, "eventId");
        Checks.requireNonBlank(topic, "topic");
        Checks.requireNonBlank(key, "key");
        Checks.requireNonNull(occurredAt, "occurredAt");
        Checks.requireNonNull(payload, "payload");
    }

    /**
     * Enveloppe un contenu au moment de sa publication : nouvel identifiant, date de l'horloge,
     * et {@code correlationId} de la requête en cours (MDC). Utilisé par les drivers.
     */
    public static <E> EventEnvelope<E> wrap(Topic<E> topic, String key, E payload, Clock clock) {
        Checks.requireNonNull(topic, "topic");
        Checks.require(topic.payloadType().isInstance(payload),
                "payload must be a " + topic.payloadType().getSimpleName() + " for topic " + topic.name());
        return new EventEnvelope<>(UUID.randomUUID().toString(), topic.name(), key, clock.instant(),
                MDC.get(LogKeys.CORRELATION_ID), payload);
    }
}
