package tn.eniso.computehive.drivers.brokerkafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import tn.eniso.computehive.core.infra.ref.MachineRef;
import tn.eniso.computehive.extensions.broker.api.EventEnvelope;
import tn.eniso.computehive.extensions.broker.api.Topic;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventCodecTest {

    /** Contenu typique : un record avec une référence étendue et une date. */
    record MachineLost(MachineRef machine, Instant lastHeartbeat) {
    }

    private static final Topic<MachineLost> TOPIC = Topic.of("tests.machine-lost", MachineLost.class);
    private static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");

    private final EventCodec codec = new EventCodec(EventCodec.defaultObjectMapper());

    private static EventEnvelope<MachineLost> event(String correlationId) {
        return new EventEnvelope<>("e-1", TOPIC.name(), "m-1", T0, correlationId,
                new MachineLost(new MachineRef("m-1", "labo-pc-12", "GPU"), T0.minusSeconds(31)));
    }

    private static ConsumerRecord<String, String> received(ProducerRecord<String, String> sent) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(sent.topic(), 0, 42L, sent.key(), sent.value());
        sent.headers().forEach(header -> record.headers().add(header));
        return record;
    }

    @Test
    void anEventSurvivesTheRoundTripThroughKafka() {
        ProducerRecord<String, String> sent = codec.encode(event("req-42"));

        EventEnvelope<MachineLost> decoded = codec.decode(received(sent), TOPIC);

        assertThat(sent.key()).isEqualTo("m-1");
        assertThat(sent.value()).contains("\"lastHeartbeat\":\"2026-11-21T09:59:29Z\"");
        assertThat(decoded).isEqualTo(event("req-42"));
    }

    @Test
    void theCorrelationIdIsOptional() {
        EventEnvelope<MachineLost> decoded = codec.decode(received(codec.encode(event(null))), TOPIC);

        assertThat(decoded.correlationId()).isNull();
    }

    @Test
    void unknownJsonFieldsAreIgnored() {
        ConsumerRecord<String, String> record = received(codec.encode(event(null)));
        ConsumerRecord<String, String> newer = new ConsumerRecord<>(record.topic(), 0, 43L, record.key(),
                record.value().replace("{\"machine\"", "{\"addedLater\":true,\"machine\""));
        record.headers().forEach(header -> newer.headers().add(header));

        assertThat(codec.decode(newer, TOPIC).payload().machine().name()).isEqualTo("labo-pc-12");
    }

    @Test
    void unreadableMessagesAreReportedClearly() {
        ConsumerRecord<String, String> notJson = received(codec.encode(event(null)));
        ConsumerRecord<String, String> broken = new ConsumerRecord<>(notJson.topic(), 0, 44L, "m-1", "{oops");
        notJson.headers().forEach(header -> broken.headers().add(header));
        ConsumerRecord<String, String> noHeaders = new ConsumerRecord<>(TOPIC.name(), 0, 45L, "m-1", notJson.value());
        ConsumerRecord<String, String> badDate = received(codec.encode(event(null)));
        badDate.headers().remove(EventCodec.OCCURRED_AT);
        badDate.headers().add(EventCodec.OCCURRED_AT, "yesterday".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> codec.decode(broken, TOPIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tests.machine-lost-0@44");
        assertThatThrownBy(() -> codec.decode(noHeaders, TOPIC)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode(badDate, TOPIC)).isInstanceOf(IllegalArgumentException.class);
    }
}
