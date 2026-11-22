package tn.eniso.computehive.app.main;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Import;
import tn.eniso.computehive.drivers.brokerkafka.KafkaBrokerConfiguration;
import tn.eniso.computehive.drivers.notifemail.EmailNotifierConfiguration;

/**
 * ComputeHive en M/P, processus principal : jobs, machines et quotas, avec l'API des utilisateurs.
 *
 * <p>Même code que le modulith ; seul l'assemblage change (le {@code pom.xml} et cette classe) :</p>
 * <ul>
 *   <li><b>modules</b> : trouvés par scan, comme en M/1. Le classpath décide de l'implémentation : jobs, machines et
 *       quotas y sont en {@code service-impl} ; scheduling n'y est qu'en {@code service-rest-cli}, son proxy vers
 *       {@code app-scheduling} ({@code computehive.remote.scheduling.url}) ;</li>
 *   <li><b>drivers</b> : Kafka, pour que les événements ({@code MachineDead}, {@code JobCancelled}) atteignent
 *       scheduling dans l'autre processus, et l'e-mail pour les notifications.</li>
 * </ul>
 *
 * <p>L'auto-configuration Kafka de Spring Boot est écartée : le driver crée lui-même ses clients.</p>
 */
@SpringBootApplication(
        scanBasePackages = {"tn.eniso.computehive.core", "tn.eniso.computehive.modules"},
        exclude = KafkaAutoConfiguration.class)
@Import({KafkaBrokerConfiguration.class, EmailNotifierConfiguration.class})
public class MainApplication {

    public static void main(String[] args) {
        SpringApplication.run(MainApplication.class, args);
    }
}
