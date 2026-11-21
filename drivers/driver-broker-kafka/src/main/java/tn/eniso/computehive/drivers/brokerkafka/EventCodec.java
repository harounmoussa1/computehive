package tn.eniso.computehive.drivers.brokerkafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import tn.eniso.computehive.extensions.broker.api.EventEnvelope;
import tn.eniso.computehive.extensions.broker.api.Topic;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Format d'un événement dans Kafka : le contenu en JSON dans la valeur du message, la clé d'ordre
 * dans la clé Kafka, et les métadonnées de l'enveloppe dans les en-têtes.
 *
 * <p>À la lecture, les champs JSON inconnus sont ignorés : un abonné plus ancien comprend encore
 * un événement auquel un champ a été ajouté.</p>
 */
final class EventCodec {

    static final String EVENT_ID = "computehive-event-id";
    static final String OCCURRED_AT = "computehive-occurred-at";
    static final String CORRELATION_ID = "computehive-correlation-id";

    private final ObjectMapper json;

    EventCodec(ObjectMapper json) {
        this.json = json;
    }

    /** JSON lisible et stable : dates ISO-8601, champs inconnus tolérés. */
    static ObjectMapper defaultObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    ProducerRecord<String, String> encode(EventEnvelope<?> event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(event.topic(), event.key(), toJson(event.payload()));
        record.headers().add(EVENT_ID, bytes(event.eventId()));
        record.headers().add(OCCURRED_AT, bytes(event.occurredAt().toString()));
        if (event.correlationId() != null) {
            record.headers().add(CORRELATION_ID, bytes(event.correlationId()));
        }
        return record;
    }

    /**
     * Reconstruit l'enveloppe d'un message reçu.
     *
     * @throws IllegalArgumentException si le message est illisible (JSON invalide, en-tête manquant…)
     */
    <E> EventEnvelope<E> decode(ConsumerRecord<String, String> record, Topic<E> topic) {
        try {
            E payload = json.readValue(record.value(), topic.payloadType());
            return new EventEnvelope<>(header(record.headers(), EVENT_ID), record.topic(), record.key(),
                    Instant.parse(header(record.headers(), OCCURRED_AT)), header(record.headers(), CORRELATION_ID),
                    payload);
        } catch (JsonProcessingException | RuntimeException e) {
            throw new IllegalArgumentException("Unreadable event at " + record.topic() + "-" + record.partition()
                    + "@" + record.offset() + ": " + e.getMessage(), e);
        }
    }

    private String toJson(Object payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Event payload cannot be written as JSON: "
                    + payload.getClass().getName(), e);
        }
    }

    private static String header(Headers headers, String name) {
        Header header = headers.lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
