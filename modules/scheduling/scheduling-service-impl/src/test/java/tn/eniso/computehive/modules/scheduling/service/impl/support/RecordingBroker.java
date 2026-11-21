package tn.eniso.computehive.modules.scheduling.service.impl.support;

import tn.eniso.computehive.extensions.broker.api.EventEnvelope;
import tn.eniso.computehive.extensions.broker.api.EventHandler;
import tn.eniso.computehive.extensions.broker.api.MessageBroker;
import tn.eniso.computehive.extensions.broker.api.Subscription;
import tn.eniso.computehive.extensions.broker.api.Topic;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Faux broker pour les tests du module (qui ne peut pas dépendre d'un driver) : garde les abonnements et
 * permet de livrer un événement à la main, de façon synchrone.
 */
public class RecordingBroker implements MessageBroker {

    private final Map<String, EventHandler<?>> handlers = new HashMap<>();

    @Override
    public <E> void publish(Topic<E> topic, String key, E payload) {
        deliver(topic, key, payload);
    }

    @Override
    public <E> Subscription subscribe(Topic<E> topic, String subscriber, EventHandler<E> handler) {
        String id = topic.name() + "/" + subscriber;
        handlers.put(id, handler);
        return () -> handlers.remove(id);
    }

    /** Livre l'événement à tous les abonnés du topic. */
    @SuppressWarnings("unchecked")
    public <E> void deliver(Topic<E> topic, String key, E payload) {
        EventEnvelope<E> event = new EventEnvelope<>(UUID.randomUUID().toString(), topic.name(), key, Instant.now(),
                null, payload);
        handlers.forEach((id, handler) -> {
            if (id.startsWith(topic.name() + "/")) {
                ((EventHandler<E>) handler).handle(event);
            }
        });
    }

    public boolean isSubscribed(Topic<?> topic, String subscriber) {
        return handlers.containsKey(topic.name() + "/" + subscriber);
    }
}
