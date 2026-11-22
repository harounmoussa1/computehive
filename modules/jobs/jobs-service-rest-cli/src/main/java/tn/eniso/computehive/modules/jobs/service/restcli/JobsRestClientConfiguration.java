package tn.eniso.computehive.modules.jobs.service.restcli;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tn.eniso.computehive.core.service.restcli.RemoteModuleClients;
import tn.eniso.computehive.modules.jobs.service.api.JobsModule;

/**
 * Fournit {@link JobsModule} à une application qui n'héberge pas le module jobs : à la place de
 * {@code jobs-service-impl}, l'assemblage ajoute ce sous-projet et l'adresse {@code computehive.remote.jobs.url}.
 */
@Configuration(proxyBeanMethods = false)
public class JobsRestClientConfiguration {

    @Bean
    JobsModule jobsModule(RemoteModuleClients clients, @Value("${computehive.remote.jobs.url}") String url) {
        return new JobsModuleRestClient(clients.create("jobs", url));
    }
}
