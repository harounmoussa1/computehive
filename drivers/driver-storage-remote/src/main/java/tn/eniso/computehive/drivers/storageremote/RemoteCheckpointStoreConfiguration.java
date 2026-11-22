package tn.eniso.computehive.drivers.storageremote;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Active le driver de stockage distant simulé : à importer par l'application à la place du driver local.
 * Réglages : {@link RemoteStorageProperties} ({@code computehive.checkpoints.remote.*}).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RemoteStorageProperties.class)
public class RemoteCheckpointStoreConfiguration {

    @Bean
    SimulatedRemoteBucket simulatedRemoteBucket(RemoteStorageProperties properties) {
        return new SimulatedRemoteBucket(properties.latency(), properties.failureRate(), System.nanoTime());
    }

    @Bean
    RemoteCheckpointStore checkpointStore(SimulatedRemoteBucket bucket, RemoteStorageProperties properties,
                                          ObjectProvider<Clock> clock) {
        return new RemoteCheckpointStore(bucket, properties, clock.getIfAvailable(Clock::systemUTC));
    }
}
