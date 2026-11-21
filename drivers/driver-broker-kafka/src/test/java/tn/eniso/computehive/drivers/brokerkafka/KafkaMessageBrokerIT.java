package tn.eniso.computehive.drivers.brokerkafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tn.eniso.computehive.core.infra.log.LogKeys;
import tn.eniso.computehive.extensions.broker.api.EventEnvelope;
import tn.eniso.computehive.extensions.broker.api.Subscription;
import tn.eniso.computehive.extensions.broker.api.Topic;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste le driver sur un vrai Kafka : il doit offrir les mêmes garanties que le driver mémoire
 * (contrat {@code MessageBroker}), plus la reprise après redémarrage d'un abonné.
 * Ignoré si Docker n'est pas disponible sur la machine.
 */
@Testcontainers(disabledWithoutDocker = true)
class KafkaMessageBrokerIT {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.7.1");

    record Ping(int value) {
    }

    private static final AtomicInteger TOPICS = new AtomicInteger();

    private final List<KafkaMessageBroker> brokers = new ArrayList<>();

    /** Un topic neuf par test : les tests ne se voient pas entre eux. */
    private static Topic<Ping> newTopic() {
        return Topic.of("tests.ping-" + TOPICS.incrementAndGet(), Ping.class);
    }

    private KafkaMessageBroker newBroker() {
        KafkaMessageBroker broker = new KafkaMessageBroker(
                KafkaBrokerProperties.forServers(KAFKA.getBootstrapServers()), Clock.systemUTC());
        brokers.add(broker);
        return broker;
    }

    @AfterEach
    void tearDown() {
        brokers.forEach(KafkaMessageBroker::close);
        MDC.clear();
    }

    @Test
    void deliversTheEventWithItsMetadataAndCorrelationId() throws Exception {
        Topic<Ping> topic = newTopic();
        KafkaMessageBroker broker = newBroker();
        BlockingQueue<EventEnvelope<Ping>> received = new LinkedBlockingQueue<>();
        List<String> correlationIds = new CopyOnWriteArrayList<>();
        broker.subscribe(topic, "audit", e -> {
            correlationIds.add(MDC.get(LogKeys.CORRELATION_ID));
            received.add(e);
        });
        MDC.put(LogKeys.CORRELATION_ID, "req-42");

        broker.publish(topic, "m-1", new Ping(7));

        EventEnvelope<Ping> event = received.poll(20, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.payload()).isEqualTo(new Ping(7));
        assertThat(event.key()).isEqualTo("m-1");
        assertThat(event.topic()).isEqualTo(topic.name());
        assertThat(event.correlationId()).isEqualTo("req-42");
        assertThat(correlationIds).containsExactly("req-42");
    }

    @Test
    void everySubscriberReceivesEachEvent() throws Exception {
        Topic<Ping> topic = newTopic();
        KafkaMessageBroker broker = newBroker();
        BlockingQueue<Integer> scheduling = new LinkedBlockingQueue<>();
        BlockingQueue<Integer> dashboard = new LinkedBlockingQueue<>();
        broker.subscribe(topic, "scheduling", e -> scheduling.add(e.payload().value()));
        broker.subscribe(topic, "dashboard", e -> dashboard.add(e.payload().value()));

        broker.publish(topic, "m-1", new Ping(1));

        assertThat(scheduling.poll(20, TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(dashboard.poll(20, TimeUnit.SECONDS)).isEqualTo(1);
    }

    @Test
    void eventsOfTheSameKeyArriveInOrder() throws Exception {
        Topic<Ping> topic = newTopic();
        KafkaMessageBroker broker = newBroker();
        BlockingQueue<Integer> received = new LinkedBlockingQueue<>();
        broker.subscribe(topic, "ordered", e -> received.add(e.payload().value()));

        for (int i = 0; i < 50; i++) {
            broker.publish(topic, "m-1", new Ping(i));
        }

        List<Integer> values = take(received, 50);
        assertThat(values).containsExactlyElementsOf(java.util.stream.IntStream.range(0, 50).boxed().toList());
    }

    @Test
    void twoInstancesWithTheSameSubscriberNameShareTheEvents() throws Exception {
        Topic<Ping> topic = newTopic();
        KafkaMessageBroker publisher = newBroker();
        BlockingQueue<Integer> received = new LinkedBlockingQueue<>();
        List<Integer> first = new CopyOnWriteArrayList<>();
        List<Integer> second = new CopyOnWriteArrayList<>();
        newBroker().subscribe(topic, "scheduling", e -> {
            first.add(e.payload().value());
            received.add(e.payload().value());
        });
        newBroker().subscribe(topic, "scheduling", e -> {
            second.add(e.payload().value());
            received.add(e.payload().value());
        });

        for (int i = 0; i < 30; i++) {
            publisher.publish(topic, "m-" + i, new Ping(i));
        }

        List<Integer> values = take(received, 30);
        assertThat(new HashSet<>(values)).as("each event handled once by the group").hasSize(30);
        assertThat(first).as("first instance got a share").isNotEmpty();
        assertThat(second).as("second instance got a share").isNotEmpty();
    }

    @Test
    void aFailingSubscriberIsRetriedThenTheEventIsSkipped() throws Exception {
        Topic<Ping> topic = newTopic();
        KafkaMessageBroker broker = newBroker();
        AtomicInteger attempts = new AtomicInteger();
        BlockingQueue<Integer> handled = new LinkedBlockingQueue<>();
        broker.subscribe(topic, "picky", e -> {
            if (e.payload().value() == 1) {
                attempts.incrementAndGet();
                throw new IllegalStateException("cannot handle 1");
            }
            handled.add(e.payload().value());
        });

        broker.publish(topic, "m-1", new Ping(1));
        broker.publish(topic, "m-1", new Ping(2));

        assertThat(handled.poll(20, TimeUnit.SECONDS)).isEqualTo(2);
        assertThat(attempts).hasValue(KafkaMessageBroker.MAX_ATTEMPTS);
    }

    @Test
    void anUnreadableMessageIsSkipped() throws Exception {
        Topic<Ping> topic = newTopic();
        KafkaMessageBroker broker = newBroker();
        BlockingQueue<Integer> handled = new LinkedBlockingQueue<>();
        broker.subscribe(topic, "robust", e -> handled.add(e.payload().value()));

        try (KafkaProducer<String, String> raw = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            raw.send(new ProducerRecord<>(topic.name(), "m-1", "this is not an event")).get();
        }
        broker.publish(topic, "m-1", new Ping(3));

        assertThat(handled.poll(20, TimeUnit.SECONDS)).isEqualTo(3);
    }

    @Test
    void aRestartedSubscriberGetsWhatWasPublishedWhileItWasAway() throws Exception {
        Topic<Ping> topic = newTopic();
        KafkaMessageBroker publisher = newBroker();
        BlockingQueue<Integer> received = new LinkedBlockingQueue<>();
        Subscription firstRun = newBroker().subscribe(topic, "scheduling", e -> received.add(e.payload().value()));
        publisher.publish(topic, "m-1", new Ping(1));
        assertThat(received.poll(20, TimeUnit.SECONDS)).isEqualTo(1);

        firstRun.close();
        publisher.publish(topic, "m-1", new Ping(2));
        newBroker().subscribe(topic, "scheduling", e -> received.add(e.payload().value()));

        assertThat(received.poll(20, TimeUnit.SECONDS)).as("published while the subscriber was stopped").isEqualTo(2);
    }

    private static List<Integer> take(BlockingQueue<Integer> queue, int count) throws InterruptedException {
        List<Integer> values = new ArrayList<>();
        while (values.size() < count) {
            Integer value = queue.poll(20, TimeUnit.SECONDS);
            assertThat(value).as("received %d of %d events", values.size(), count).isNotNull();
            values.add(value);
        }
        return values;
    }
}
