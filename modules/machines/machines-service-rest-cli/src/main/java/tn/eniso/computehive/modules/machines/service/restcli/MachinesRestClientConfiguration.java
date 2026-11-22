package tn.eniso.computehive.modules.machines.service.restcli;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tn.eniso.computehive.core.service.restcli.RemoteModuleClients;
import tn.eniso.computehive.modules.machines.service.api.MachinesModule;

/**
 * Fournit {@link MachinesModule} à une application qui n'héberge pas le module machines : à la place de
 * {@code machines-service-impl}, l'assemblage ajoute ce sous-projet et l'adresse
 * {@code computehive.remote.machines.url}.
 */
@Configuration(proxyBeanMethods = false)
public class MachinesRestClientConfiguration {

    @Bean
    MachinesModule machinesModule(RemoteModuleClients clients,
                                  @Value("${computehive.remote.machines.url}") String url) {
        return new MachinesModuleRestClient(clients.create("machines", url));
    }
}
