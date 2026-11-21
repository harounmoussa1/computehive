package tn.eniso.computehive.drivers.brokermemory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tn.eniso.computehive.core.infra.util.Checks;
import tn.eniso.computehive.extensions.broker.api.EventDelivery;
import tn.eniso.computehive.extensions.broker.api.EventEnvelope;
import tn.eniso.computehive.extensions.broker.api.EventHandler;
import tn.eniso.computehive.extensions.broker.api.MessageBroker;
import tn.eniso.computehive.extensions.broker.api.Subscription;
import tn.eniso.computehive.extensions.broker.api.Topic;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Broker en mémoire, pour le mode M/1 et les tests. Il respecte les garanties de
 * {@link MessageBroker} comme le driver Kafka :
 *
 * <ul>
 *   <li><b>asynchrone</b> : les événements sont livrés par un fil dédié ({@value #THREAD_NAME}),
 *       jamais dans le fil (ni la transaction) de celui qui publie ;</li>
 *   <li><b>ordre</b> : un seul fil de livraison, donc les événements sont livrés dans l'ordre de publication ;</li>
 *   <li><b>échecs</b> : un abonné qui échoue est retenté {@value #MAX_ATTEMPTS} fois au total, puis
 *       l'événement est journalisé et abandonné.</li>
 * </ul>
 *
 * <p>Limite assumée : les événements ne survivent pas à un arrêt du processus (rien n'est persisté).</p>
 */
public class InMemoryMessageBroker implements MessageBroker, AutoCloseable {

    static final String THREAD_NAME = "broker-memory";
    static final int MAX_ATTEMPTS = 3;

    private static final Logger log = LoggerFactory.getLogger(InMemoryMessageBroker.class);

    private final Clock clock;
    private final ThreadPoolExecutor delivery;
    /** Topic → abonné → traitement. */
    private final Map<String, Map<String, EventHandler<?>>> subscribers = new ConcurrentHashMap<>();

    public InMemoryMessageBroker(Clock clock) {
        this.clock = Checks.requireNonNull(clock, "clock");
        this.delivery = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), task -> {
            Thread thread = new Thread(task, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public <E> void publish(Topic<E> topic, String key, E payload) {
        EventEnvelope<E> event = EventEnvelope.wrap(topic, key, payload, clock);
        Map<String, EventHandler<?>> current = subscribers.getOrDefault(topic.name(), Map.of());
        log.debug("Publishing event {} on {} (key {}) to {} subscriber(s)",
                event.eventId(), topic.name(), key, current.size());
        current.forEach((subscriber, handler) -> submit(() -> deliver(event, subscriber, handler)));
    }

    @Override
    public <E> Subscription subscribe(Topic<E> topic, String subscriber, EventHandler<E> handler) {
        Checks.requireNonNull(topic, "topic");
        Checks.requireNonBlank(subscriber, "subscriber");
        Checks.requireNonNull(handler, "handler");
        Map<String, EventHandler<?>> topicSubscribers =
                subscribers.computeIfAbsent(topic.name(), name -> new ConcurrentHashMap<>());
        if (topicSubscribers.putIfAbsent(subscriber, handler) != null) {
            throw new IllegalStateException(subscriber + " is already subscribed to " + topic.name());
        }
        log.info("{} subscribed to {}", subscriber, topic.name());
        return () -> topicSubscribers.remove(subscriber, handler);
    }

    /**
     * Attend que tous les événements publiés jusqu'ici soient livrés, y compris ceux que les abonnés
     * publient à leur tour pendant ce temps. Utile aux tests d'intégration en M/1.
     *
     * @throws IllegalStateException si la livraison n'est pas terminée après {@code timeout}
     */
    public void awaitDelivery(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        boolean idle;
        do {
            // Le fil est unique : quand ce marqueur s'exécute, tout ce qui le précède est livré.
            // La file ne contient alors que ce qui a été publié entre-temps.
            Future<Boolean> marker = delivery.submit(() -> delivery.getQueue().isEmpty());
            try {
                idle = marker.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                throw new IllegalStateException("Events still being delivered after " + timeout, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for event delivery", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException(e.getCause());
            }
        } while (!idle);
    }

    /** Termine la livraison des événements en attente (5 s au plus), puis arrête le fil. */
    @Override
    public void close() {
        delivery.shutdown();
        try {
            if (!delivery.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Undelivered events dropped at shutdown: {}", delivery.shutdownNow().size());
            }
        } catch (InterruptedException e) {
            delivery.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** Livre l'événement si l'abonné l'est toujours (il a pu se désabonner depuis la publication). */
    @SuppressWarnings("unchecked")
    private <E> void deliver(EventEnvelope<E> event, String subscriber, EventHandler<?> handler) {
        if (subscribers.getOrDefault(event.topic(), Map.of()).get(subscriber) != handler) {
            return;
        }
        EventDelivery.deliver(event, subscriber, (EventHandler<E>) handler, MAX_ATTEMPTS);
    }

    private void submit(Runnable task) {
        try {
            delivery.execute(task);
        } catch (RejectedExecutionException e) {
            throw new IllegalStateException("Message broker is closed", e);
        }
    }
}
