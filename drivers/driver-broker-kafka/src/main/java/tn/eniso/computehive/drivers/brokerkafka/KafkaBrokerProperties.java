package tn.eniso.computehive.drivers.brokerkafka;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Réglages du driver Kafka ({@code computehive.broker.kafka.*}).
 *
 * @param bootstrapServers  adresse(s) du cluster Kafka, ex. {@code kafka:9092} dans Docker Compose
 * @param clientId          préfixe des identifiants de client Kafka (visible dans les outils d'administration)
 * @param partitions        nombre de partitions des topics créés par le driver
 * @param replicationFactor nombre de copies de chaque partition (1 pour un seul broker Kafka)
 * @param subscribeTimeout  délai maximal pour qu'un abonnement reçoive ses partitions
 */
@ConfigurationProperties("computehive.broker.kafka")
public record KafkaBrokerProperties(
        @DefaultValue("localhost:9092") String bootstrapServers,
        @DefaultValue("computehive") String clientId,
        @DefaultValue("3") int partitions,
        @DefaultValue("1") short replicationFactor,
        @DefaultValue("30s") Duration subscribeTimeout) {

    public KafkaBrokerProperties {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            throw new IllegalArgumentException("computehive.broker.kafka.bootstrap-servers must not be blank");
        }
        if (partitions < 1) {
            throw new IllegalArgumentException("computehive.broker.kafka.partitions must be at least 1");
        }
        if (replicationFactor < 1) {
            throw new IllegalArgumentException("computehive.broker.kafka.replication-factor must be at least 1");
        }
        if (subscribeTimeout.isNegative() || subscribeTimeout.isZero()) {
            throw new IllegalArgumentException("computehive.broker.kafka.subscribe-timeout must be positive");
        }
    }

    /** Valeurs par défaut, pour un Kafka donné. */
    public static KafkaBrokerProperties forServers(String bootstrapServers) {
        return new KafkaBrokerProperties(bootstrapServers, "computehive", 3, (short) 1, Duration.ofSeconds(30));
    }
}
