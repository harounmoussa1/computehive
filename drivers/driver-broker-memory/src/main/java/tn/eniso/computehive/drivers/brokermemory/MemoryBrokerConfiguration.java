package tn.eniso.computehive.drivers.brokermemory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Active le driver mémoire : à importer par l'application (M/1, tests) à la place du driver Kafka.
 * Le choix du driver se fait uniquement ici, à l'assemblage ; les modules ne voient que {@code MessageBroker}.
 */
@Configuration(proxyBeanMethods = false)
public class MemoryBrokerConfiguration {

    /** Utilise l'horloge de l'application ({@code core-service-impl}) si elle existe. */
    @Bean(destroyMethod = "close")
    InMemoryMessageBroker messageBroker(ObjectProvider<Clock> clock) {
        return new InMemoryMessageBroker(clock.getIfAvailable(Clock::systemUTC));
    }
}
