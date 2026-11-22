package tn.eniso.computehive.core.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tn.eniso.computehive.core.infra.security.AgentToken;
import tn.eniso.computehive.core.infra.security.InternalToken;

import java.time.Clock;

/** Beans techniques partagés par les services de tous les modules. */
@Configuration(proxyBeanMethods = false)
public class CoreServiceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CoreServiceConfiguration.class);

    /**
     * Horloge unique de l'application (UTC). Les services l'injectent au lieu d'appeler
     * {@code Instant.now()}, ce qui permet de contrôler le temps dans les tests.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Jeton partagé des agents ({@code computehive.agents.token}, variable {@code COMPUTEHIVE_AGENTS_TOKEN}),
     * vérifié par la porte gRPC et par l'API des checkpoints. Sans jeton, tout agent est accepté : à réserver au
     * développement, d'où l'avertissement.
     */
    @Bean
    AgentToken agentToken(@Value("${computehive.agents.token:}") String token) {
        AgentToken agentToken = AgentToken.of(token);
        if (!agentToken.enabled()) {
            log.warn("Agent authentication is DISABLED: any program can register as an agent. "
                    + "Set computehive.agents.token (COMPUTEHIVE_AGENTS_TOKEN) outside development.");
        }
        return agentToken;
    }

    /**
     * Jeton entre applications du mode M/P ({@code computehive.internal.token}, {@code COMPUTEHIVE_INTERNAL_TOKEN}),
     * vérifié sur les endpoints {@code /internal/**} et présenté par les proxys {@code service-rest-cli}.
     */
    @Bean
    InternalToken internalToken(@Value("${computehive.internal.token:}") String token) {
        return InternalToken.of(token);
    }
}
