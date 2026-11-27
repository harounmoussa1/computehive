package tn.eniso.computehive.drivers.storageremote;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Active le driver de stockage distant : à importer par l'application à la place du driver local. Il fournit les
 * deux contrats de stockage, sur le même service et derrière le même circuit : les checkpoints
 * ({@link RemoteCheckpointStore}) et les fichiers des utilisateurs ({@link RemoteFileStore}).
 * Réglages : {@link RemoteStorageProperties} ({@code computehive.checkpoints.remote.*}).
 *
 * <p>Avec l'adresse d'un service compatible S3 ({@code computehive.checkpoints.remote.s3.endpoint}), les fichiers
 * vont sur ce service ; sinon, sur un stockage simulé en mémoire, perdu à l'arrêt (un avertissement le signale).</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RemoteStorageProperties.class)
public class RemoteStorageConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RemoteStorageConfiguration.class);

    @Bean
    RemoteBucket remoteBucket(RemoteStorageProperties properties) {
        RemoteStorageProperties.S3 s3 = properties.s3();
        if (s3.configured()) {
            log.info("Files stored on S3 storage at {} (bucket {})", s3.endpoint(), s3.bucket());
            return new S3RemoteBucket(s3.endpoint(), s3.accessKey(), s3.secretKey(), s3.bucket());
        }
        log.warn("No S3 endpoint (computehive.checkpoints.remote.s3.endpoint): files are kept in memory "
                + "by a simulated store and will be lost at shutdown");
        return new SimulatedRemoteBucket(properties.latency(), properties.failureRate(), System.nanoTime());
    }

    /** L'état du circuit est publié dans le registre de métriques de l'application, s'il y en a un (ADR 0014). */
    @Bean
    ResilientStorage resilientStorage(RemoteStorageProperties properties, ObjectProvider<MeterRegistry> meters) {
        ResilientStorage resilience = new ResilientStorage(properties);
        meters.ifAvailable(resilience::bindTo);
        return resilience;
    }

    @Bean
    RemoteCheckpointStore checkpointStore(RemoteBucket bucket, ResilientStorage resilience,
                                          ObjectProvider<Clock> clock) {
        return new RemoteCheckpointStore(bucket, resilience, clock.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    RemoteFileStore fileStore(RemoteBucket bucket, ResilientStorage resilience, ObjectProvider<Clock> clock) {
        return new RemoteFileStore(bucket, resilience, clock.getIfAvailable(Clock::systemUTC));
    }
}
