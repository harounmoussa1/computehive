package tn.eniso.computehive.drivers.brokerkafka;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Active le driver Kafka : à importer par les applications du mode M/P à la place du driver mémoire.
 * Réglages : {@link KafkaBrokerProperties} ({@code computehive.broker.kafka.*}).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KafkaBrokerProperties.class)
public class KafkaBrokerConfiguration {

    /** Utilise l'horloge de l'application ({@code core-service-impl}) si elle existe. */
    @Bean(destroyMethod = "close")
    KafkaMessageBroker messageBroker(KafkaBrokerProperties properties, ObjectProvider<Clock> clock) {
        return new KafkaMessageBroker(properties, clock.getIfAvailable(Clock::systemUTC));
    }
}
