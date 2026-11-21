package tn.eniso.computehive.extensions.broker.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.core.infra.log.LogKeys;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerContractTest {

    record Ping(String value) {
    }

    private static final Topic<Ping> PING = Topic.of("tests.ping", Ping.class);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T10:00:00Z"), ZoneOffset.UTC);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void topicNamesFollowTheModuleDotEventConvention() {
        assertThat(Topic.of("machines.machine-dead", Ping.class).name()).isEqualTo("machines.machine-dead");
        assertThatThrownBy(() -> Topic.of("MachineDead", Ping.class)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Topic.of("machines", Ping.class)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Topic.of("machines.dead", null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void wrapAddsAnIdTheDateAndTheCurrentCorrelationId() {
        MDC.put(LogKeys.CORRELATION_ID, "req-42");

        EventEnvelope<Ping> first = EventEnvelope.wrap(PING, "k-1", new Ping("a"), CLOCK);
        EventEnvelope<Ping> second = EventEnvelope.wrap(PING, "k-1", new Ping("a"), CLOCK);

        assertThat(first.eventId()).isNotEqualTo(second.eventId());
        assertThat(first.topic()).isEqualTo("tests.ping");
        assertThat(first.occurredAt()).isEqualTo(CLOCK.instant());
        assertThat(first.correlationId()).isEqualTo("req-42");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void wrapRejectsAPayloadOfTheWrongType() {
        Topic raw = PING;

        assertThatThrownBy(() -> EventEnvelope.wrap(raw, "k-1", "not a ping", CLOCK))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void deliveryRunsTheHandlerWithTheEventCorrelationIdThenRestoresTheMdc() {
        MDC.put(LogKeys.CORRELATION_ID, "consumer-thread");
        EventEnvelope<Ping> event = new EventEnvelope<>("e-1", "tests.ping", "k-1", CLOCK.instant(), "req-42",
                new Ping("a"));
        List<String> seen = new ArrayList<>();

        boolean delivered = EventDelivery.deliver(event, "tests", e -> seen.add(MDC.get(LogKeys.CORRELATION_ID)), 3);

        assertThat(delivered).isTrue();
        assertThat(seen).containsExactly("req-42");
        assertThat(MDC.get(LogKeys.CORRELATION_ID)).isEqualTo("consumer-thread");
    }

    @Test
    void aFailingHandlerIsRetriedThenTheEventIsDropped() {
        EventEnvelope<Ping> event = new EventEnvelope<>("e-1", "tests.ping", "k-1", CLOCK.instant(), null,
                new Ping("a"));
        AtomicInteger calls = new AtomicInteger();

        boolean delivered = EventDelivery.deliver(event, "tests", e -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        }, 3);

        assertThat(delivered).isFalse();
        assertThat(calls).hasValue(3);
    }

    @Test
    void aHandlerThatRecoversOnRetrySucceeds() {
        EventEnvelope<Ping> event = new EventEnvelope<>("e-1", "tests.ping", "k-1", CLOCK.instant(), null,
                new Ping("a"));
        AtomicInteger calls = new AtomicInteger();

        boolean delivered = EventDelivery.deliver(event, "tests", e -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("transient");
            }
        }, 3);

        assertThat(delivered).isTrue();
        assertThat(calls).hasValue(2);
    }
}
