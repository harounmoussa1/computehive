package tn.eniso.computehive.drivers.notiflog;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Active le driver de notifications par logs : à importer par l'application (M/1, tests) à la place du driver
 * e-mail. Le choix se fait uniquement ici ; les modules ne voient que {@code Notifier}.
 */
@Configuration(proxyBeanMethods = false)
public class LogNotifierConfiguration {

    @Bean
    LogNotifier notifier() {
        return new LogNotifier();
    }
}
