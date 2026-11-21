package tn.eniso.computehive.drivers.brokerkafka;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import tn.eniso.computehive.core.infra.util.Checks;
import tn.eniso.computehive.extensions.broker.api.EventDelivery;
import tn.eniso.computehive.extensions.broker.api.EventEnvelope;
import tn.eniso.computehive.extensions.broker.api.EventHandler;
import tn.eniso.computehive.extensions.broker.api.MessageBroker;
import tn.eniso.computehive.extensions.broker.api.Subscription;
import tn.eniso.computehive.extensions.broker.api.Topic;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Broker Apache Kafka, pour le mode M/P et la démonstration. Il respecte les garanties de
 * {@link MessageBroker} comme le driver mémoire :
 *
 * <ul>
 *   <li><b>asynchrone</b> : l'envoi est confié au producteur Kafka ; chaque abonnement a son propre fil
 *       de consommation ;</li>
 *   <li><b>au moins une fois</b> : la position de lecture (offset) n'est validée qu'après le traitement
 *       d'un événement ; après un arrêt brutal, le dernier événement peut être relivré ;</li>
 *   <li><b>ordre par clé</b> : une même clé va toujours dans la même partition, lue par un seul fil ;</li>
 *   <li><b>échecs</b> : un abonné qui échoue est retenté {@value #MAX_ATTEMPTS} fois au total, puis
 *       l'événement est journalisé et sauté ; un message illisible est sauté de même.</li>
 * </ul>
 *
 * <p>Un abonné est un <i>consumer group</i> Kafka : les instances d'une application abonnées sous le
 * même nom se partagent les partitions. Contrairement au driver mémoire, les événements sont conservés :
 * un abonné redémarré reprend là où il s'était arrêté.</p>
 */
public class KafkaMessageBroker implements MessageBroker, AutoCloseable {

    static final int MAX_ATTEMPTS = 3;

    private static final Logger log = LoggerFactory.getLogger(KafkaMessageBroker.class);

    private final KafkaBrokerProperties properties;
    private final Clock clock;
    private final EventCodec codec;
    private final KafkaAdmin admin;
    private final DefaultKafkaProducerFactory<String, String> producerFactory;
    private final KafkaTemplate<String, String> template;
    private final Set<String> knownTopics = ConcurrentHashMap.newKeySet();
    private final List<KafkaMessageListenerContainer<String, String>> containers = new CopyOnWriteArrayList<>();

    public KafkaMessageBroker(KafkaBrokerProperties properties, Clock clock) {
        this.properties = Checks.requireNonNull(properties, "properties");
        this.clock = Checks.requireNonNull(clock, "clock");
        this.codec = new EventCodec(EventCodec.defaultObjectMapper());
        this.admin = new KafkaAdmin(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers()));
        this.producerFactory = new DefaultKafkaProducerFactory<>(producerConfig());
        this.template = new KafkaTemplate<>(producerFactory);
    }

    @Override
    public <E> void publish(Topic<E> topic, String key, E payload) {
        EventEnvelope<E> event = EventEnvelope.wrap(topic, key, payload, clock);
        ensureTopic(topic.name());
        template.send(codec.encode(event)).whenComplete((result, error) -> {
            if (error != null) {
                log.error("Event {} on {} (key {}) could not be sent to Kafka", event.eventId(), topic.name(), key, error);
            } else {
                log.debug("Event {} sent to {}-{}@{}", event.eventId(), topic.name(),
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }

    /**
     * Rend la main une fois les partitions attribuées à l'abonné : les événements publiés ensuite
     * lui parviennent. Un abonné nouveau commence à la fin du topic (il ne reçoit pas l'historique) ;
     * un abonné déjà connu reprend à sa dernière position validée.
     *
     * @throws IllegalStateException si Kafka n'a pas attribué de partitions dans {@code subscribe-timeout}
     */
    @Override
    public <E> Subscription subscribe(Topic<E> topic, String subscriber, EventHandler<E> handler) {
        Checks.requireNonNull(topic, "topic");
        Checks.requireNonBlank(subscriber, "subscriber");
        Checks.requireNonNull(handler, "handler");
        ensureTopic(topic.name());

        CountDownLatch assigned = new CountDownLatch(1);
        ContainerProperties container = new ContainerProperties(topic.name());
        container.setGroupId(subscriber);
        container.setClientId(properties.clientId() + "-" + subscriber);
        container.setAckMode(ContainerProperties.AckMode.RECORD);
        container.setConsumerRebalanceListener(new ConsumerAwareRebalanceListener() {
            @Override
            public void onPartitionsAssigned(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
                // Fixe tout de suite la position de lecture : un événement publié juste après
                // l'abonnement ne peut pas être sauté.
                partitions.forEach(consumer::position);
                assigned.countDown();
            }
        });
        container.setMessageListener((MessageListener<String, String>) record ->
                receive(record, topic, subscriber, handler));

        KafkaMessageListenerContainer<String, String> listener =
                new KafkaMessageListenerContainer<>(new DefaultKafkaConsumerFactory<>(consumerConfig()), container);
        listener.setBeanName("broker-kafka-" + subscriber + "-" + topic.name());
        listener.start();
        containers.add(listener);
        awaitAssignment(assigned, listener, topic, subscriber);
        log.info("{} subscribed to {}", subscriber, topic.name());
        return () -> {
            listener.stop();
            containers.remove(listener);
        };
    }

    /** Arrête les abonnements (en validant leur position), puis le producteur, après envoi des messages en attente. */
    @Override
    public void close() {
        containers.forEach(KafkaMessageListenerContainer::stop);
        containers.clear();
        producerFactory.destroy();
    }

    private <E> void receive(ConsumerRecord<String, String> record, Topic<E> topic, String subscriber,
                             EventHandler<E> handler) {
        EventEnvelope<E> event;
        try {
            event = codec.decode(record, topic);
        } catch (IllegalArgumentException e) {
            log.error("{} skipped an unreadable event: {}", subscriber, e.getMessage());
            return;
        }
        EventDelivery.deliver(event, subscriber, handler, MAX_ATTEMPTS);
    }

    /** Crée le topic au premier usage, avec le nombre de partitions configuré (sans effet s'il existe). */
    private void ensureTopic(String name) {
        if (knownTopics.contains(name)) {
            return;
        }
        admin.createOrModifyTopics(TopicBuilder.name(name)
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .build());
        knownTopics.add(name);
    }

    private void awaitAssignment(CountDownLatch assigned, KafkaMessageListenerContainer<String, String> listener,
                                 Topic<?> topic, String subscriber) {
        try {
            if (assigned.await(properties.subscribeTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        listener.stop();
        containers.remove(listener);
        throw new IllegalStateException("Kafka did not assign partitions of " + topic.name() + " to " + subscriber
                + " within " + properties.subscribeTimeout());
    }

    private Map<String, Object> producerConfig() {
        return Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers(),
                ProducerConfig.CLIENT_ID_CONFIG, properties.clientId() + "-producer",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                // Écriture confirmée par toutes les copies, sans doublon en cas de nouvel essai du producteur
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
                // Si Kafka est injoignable, publish() n'attend pas plus de 10 s
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 10_000);
    }

    private Map<String, Object> consumerConfig() {
        return Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    }
}
