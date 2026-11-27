package tn.eniso.computehive.core.service.restcli;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tn.eniso.computehive.core.infra.security.InternalToken;

import java.time.Duration;

/**
 * Beans communs aux proxys {@code service-rest-cli}. Réglables par {@code computehive.remote.*} ; les valeurs par
 * défaut sont celles de {@link RemoteCallPolicy#defaults()}.
 */
@Configuration(proxyBeanMethods = false)
public class RemoteCallConfiguration {

    @Bean
    RemoteCallPolicy remoteCallPolicy(
            @Value("${computehive.remote.connect-timeout:2s}") Duration connectTimeout,
            @Value("${computehive.remote.read-timeout:5s}") Duration readTimeout,
            @Value("${computehive.remote.max-attempts:3}") int maxAttempts,
            @Value("${computehive.remote.retry-wait:200ms}") Duration retryWait,
            @Value("${computehive.remote.failure-rate-threshold:50}") float failureRateThreshold,
            @Value("${computehive.remote.sliding-window-size:10}") int slidingWindowSize,
            @Value("${computehive.remote.minimum-calls:5}") int minimumCalls,
            @Value("${computehive.remote.open-state-wait:10s}") Duration openStateWait) {
        return new RemoteCallPolicy(connectTimeout, readTimeout, maxAttempts, retryWait, failureRateThreshold,
                slidingWindowSize, minimumCalls, openStateWait);
    }

    /**
     * Reprend le {@code RestClient.Builder} de Spring Boot s'il existe (mêmes réglages JSON que les contrôleurs), et
     * le registre de métriques de l'application s'il y en a un (ADR 0014).
     */
    @Bean
    RemoteModuleClients remoteModuleClients(ObjectProvider<RestClient.Builder> builder, InternalToken token,
                                            RemoteCallPolicy policy, ObjectProvider<MeterRegistry> meters) {
        return new RemoteModuleClients(builder.getIfAvailable(RestClient::builder), token, policy,
                meters.getIfAvailable());
    }
}
