package tn.eniso.computehive.drivers.storageremote;

import io.micrometer.core.instrument.MeterRegistry;
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

    /** L'état du circuit est publié dans le registre de métriques de l'application, s'il y en a un (ADR 0014). */
    @Bean
    RemoteCheckpointStore checkpointStore(SimulatedRemoteBucket bucket, RemoteStorageProperties properties,
                                          ObjectProvider<Clock> clock, ObjectProvider<MeterRegistry> meters) {
        RemoteCheckpointStore store =
                new RemoteCheckpointStore(bucket, properties, clock.getIfAvailable(Clock::systemUTC));
        meters.ifAvailable(store::bindTo);
        return store;
    }
}
