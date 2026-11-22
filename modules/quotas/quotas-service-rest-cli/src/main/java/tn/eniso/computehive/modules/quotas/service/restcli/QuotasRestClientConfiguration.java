package tn.eniso.computehive.modules.quotas.service.restcli;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tn.eniso.computehive.core.service.restcli.RemoteModuleClients;
import tn.eniso.computehive.modules.quotas.service.api.QuotasModule;

/**
 * Fournit {@link QuotasModule} à une application qui n'héberge pas le module quotas : à la place de
 * {@code quotas-service-impl}, l'assemblage ajoute ce sous-projet et l'adresse {@code computehive.remote.quotas.url}.
 */
@Configuration(proxyBeanMethods = false)
public class QuotasRestClientConfiguration {

    @Bean
    QuotasModule quotasModule(RemoteModuleClients clients, @Value("${computehive.remote.quotas.url}") String url) {
        return new QuotasModuleRestClient(clients.create("quotas", url));
    }
}
