package tn.eniso.computehive.drivers.storagelocal;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Active le driver de stockage local : à importer par l'application à la place du driver distant.
 * Réglage : {@code computehive.checkpoints.local.directory} (défaut : {@code data/checkpoints}).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LocalCheckpointStoreConfiguration.LocalStorageProperties.class)
public class LocalCheckpointStoreConfiguration {

    /** @param directory dossier racine des checkpoints (un volume Docker en production) */
    @ConfigurationProperties("computehive.checkpoints.local")
    public record LocalStorageProperties(@DefaultValue("data/checkpoints") Path directory) {
    }

    @Bean
    LocalCheckpointStore checkpointStore(LocalStorageProperties properties, ObjectProvider<Clock> clock) {
        return new LocalCheckpointStore(properties.directory(), clock.getIfAvailable(Clock::systemUTC));
    }
}
