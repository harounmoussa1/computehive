package tn.eniso.computehive.core.service.impl;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Beans techniques partagés par les services de tous les modules. */
@Configuration(proxyBeanMethods = false)
public class CoreServiceConfiguration {

    /**
     * Horloge unique de l'application (UTC). Les services l'injectent au lieu d'appeler
     * {@code Instant.now()}, ce qui permet de contrôler le temps dans les tests.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
