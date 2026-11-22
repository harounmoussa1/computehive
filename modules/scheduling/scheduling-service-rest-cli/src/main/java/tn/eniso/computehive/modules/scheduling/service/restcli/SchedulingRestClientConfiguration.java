package tn.eniso.computehive.modules.scheduling.service.restcli;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tn.eniso.computehive.core.service.restcli.RemoteModuleClients;
import tn.eniso.computehive.modules.scheduling.service.api.SchedulingModule;

/**
 * Fournit {@link SchedulingModule} à une application qui n'héberge pas le module scheduling ({@code app-main}) :
 * à la place de {@code scheduling-service-impl}, l'assemblage ajoute ce sous-projet et l'adresse
 * {@code computehive.remote.scheduling.url}.
 */
@Configuration(proxyBeanMethods = false)
public class SchedulingRestClientConfiguration {

    @Bean
    SchedulingModule schedulingModule(RemoteModuleClients clients,
                                      @Value("${computehive.remote.scheduling.url}") String url) {
        return new SchedulingModuleRestClient(clients.create("scheduling", url));
    }
}
