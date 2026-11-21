package tn.eniso.computehive.drivers.brokermemory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tn.eniso.computehive.core.infra.log.LogKeys;
import tn.eniso.computehive.extensions.broker.api.EventEnvelope;
import tn.eniso.computehive.extensions.broker.api.Subscription;
import tn.eniso.computehive.extensions.broker.api.Topic;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryMessageBrokerTest {

    record Ping(int value) {
    }

    record Pong(int value) {
    }

    private static final Topic<Ping> PING = Topic.of("tests.ping", Ping.class);
    private static final Topic<Pong> PONG = Topic.of("tests.pong", Pong.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final InMemoryMessageBroker broker = new InMemoryMessageBroker(Clock.systemUTC());

    @AfterEach
    void tearDown() {
        broker.close();
        MDC.clear();
    }

    @Test
    void deliversEachEventToEverySubscriberOnTheBrokerThread() {
        List<String> threads = new CopyOnWriteArrayList<>();
        List<Integer> first = new CopyOnWriteArrayList<>();
        List<Integer> second = new CopyOnWriteArrayList<>();
        broker.subscribe(PING, "first", e -> {
            threads.add(Thread.currentThread().getName());
            first.add(e.payload().value());
        });
        broker.subscribe(PING, "second", e -> second.add(e.payload().value()));

        broker.publish(PING, "k", new Ping(1));
        broker.awaitDelivery(TIMEOUT);

        assertThat(first).containsExactly(1);
        assertThat(second).containsExactly(1);
        assertThat(threads).containsOnly(InMemoryMessageBroker.THREAD_NAME);
    }

    @Test
    void publishDoesNotWaitForTheSubscribers() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch handled = new CountDownLatch(1);
        broker.subscribe(PING, "slow", e -> {
            await(release);
            handled.countDown();
        });

        broker.publish(PING, "k", new Ping(1));

        assertThat(handled.getCount()).as("publish returned before delivery").isEqualTo(1);
        release.countDown();
        assertThat(handled.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void eventsAreDeliveredInPublicationOrder() {
        List<Integer> received = new CopyOnWriteArrayList<>();
        broker.subscribe(PING, "ordered", e -> received.add(e.payload().value()));

        IntStream.range(0, 200).forEach(i -> broker.publish(PING, "k", new Ping(i)));
        broker.awaitDelivery(TIMEOUT);

        assertThat(received).containsExactlyElementsOf(IntStream.range(0, 200).boxed().toList());
    }

    @Test
    void theEnvelopeCarriesTheMetadataAndTheCorrelationIdOfThePublisher() {
        List<EventEnvelope<Ping>> received = new CopyOnWriteArrayList<>();
        List<String> correlationIds = new CopyOnWriteArrayList<>();
        broker.subscribe(PING, "audit", e -> {
            received.add(e);
            correlationIds.add(MDC.get(LogKeys.CORRELATION_ID));
        });
        MDC.put(LogKeys.CORRELATION_ID, "req-42");

        broker.publish(PING, "machine-7", new Ping(1));
        broker.awaitDelivery(TIMEOUT);

        assertThat(received).singleElement().satisfies(e -> {
            assertThat(e.eventId()).isNotBlank();
            assertThat(e.topic()).isEqualTo("tests.ping");
            assertThat(e.key()).isEqualTo("machine-7");
            assertThat(e.correlationId()).isEqualTo("req-42");
        });
        assertThat(correlationIds).containsExactly("req-42");
    }

    @Test
    void onlyEventsPublishedWhileSubscribedAreReceived() {
        List<Integer> received = new CopyOnWriteArrayList<>();
        broker.publish(PING, "k", new Ping(1));

        Subscription subscription = broker.subscribe(PING, "late", e -> received.add(e.payload().value()));
        broker.publish(PING, "k", new Ping(2));
        broker.awaitDelivery(TIMEOUT);
        subscription.close();
        broker.publish(PING, "k", new Ping(3));
        broker.awaitDelivery(TIMEOUT);

        assertThat(received).containsExactly(2);
    }

    @Test
    void aSubscriberNameIsUniquePerTopic() {
        broker.subscribe(PING, "scheduling", e -> { });

        assertThatThrownBy(() -> broker.subscribe(PING, "scheduling", e -> { }))
                .isInstanceOf(IllegalStateException.class);
        broker.subscribe(PONG, "scheduling", e -> { });
    }

    @Test
    void aFailingSubscriberIsRetriedThenSkippedWithoutBlockingTheOthers() {
        AtomicInteger attempts = new AtomicInteger();
        List<Integer> healthy = new CopyOnWriteArrayList<>();
        broker.subscribe(PING, "broken", e -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("boom");
        });
        broker.subscribe(PING, "healthy", e -> healthy.add(e.payload().value()));

        broker.publish(PING, "k", new Ping(1));
        broker.publish(PING, "k", new Ping(2));
        broker.awaitDelivery(TIMEOUT);

        assertThat(attempts).hasValue(2 * InMemoryMessageBroker.MAX_ATTEMPTS);
        assertThat(healthy).containsExactly(1, 2);
    }

    @Test
    void awaitDeliveryAlsoWaitsForEventsPublishedBySubscribers() {
        List<Integer> pongs = new CopyOnWriteArrayList<>();
        broker.subscribe(PING, "relay", e -> broker.publish(PONG, "k", new Pong(e.payload().value() * 10)));
        broker.subscribe(PONG, "sink", e -> pongs.add(e.payload().value()));

        broker.publish(PING, "k", new Ping(4));
        broker.awaitDelivery(TIMEOUT);

        assertThat(pongs).containsExactly(40);
    }

    @Test
    void publishingAfterCloseFails() {
        broker.subscribe(PING, "any", e -> { });
        broker.close();

        assertThatThrownBy(() -> broker.publish(PING, "k", new Ping(1))).isInstanceOf(IllegalStateException.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
