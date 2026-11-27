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
 * Active le driver de stockage local : à importer par l'application à la place du driver distant. Il fournit les
 * deux contrats de stockage, sur le disque du serveur :
 *
 * <ul>
 *   <li>les checkpoints, dans {@code computehive.checkpoints.local.directory} (défaut : {@code data/checkpoints}) ;</li>
 *   <li>les fichiers des utilisateurs, dans {@code computehive.files.local.directory} (défaut : {@code data/files}).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({LocalStorageConfiguration.LocalStorageProperties.class,
        LocalStorageConfiguration.LocalFilesProperties.class})
public class LocalStorageConfiguration {

    /** @param directory dossier racine des checkpoints (un volume Docker en production) */
    @ConfigurationProperties("computehive.checkpoints.local")
    public record LocalStorageProperties(@DefaultValue("data/checkpoints") Path directory) {
    }

    /** @param directory dossier racine des fichiers des utilisateurs (codes, datasets, résultats) */
    @ConfigurationProperties("computehive.files.local")
    public record LocalFilesProperties(@DefaultValue("data/files") Path directory) {
    }

    @Bean
    LocalCheckpointStore checkpointStore(LocalStorageProperties properties, ObjectProvider<Clock> clock) {
        return new LocalCheckpointStore(properties.directory(), clock.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    LocalFileStore fileStore(LocalFilesProperties properties, ObjectProvider<Clock> clock) {
        return new LocalFileStore(properties.directory(), clock.getIfAvailable(Clock::systemUTC));
    }
}
