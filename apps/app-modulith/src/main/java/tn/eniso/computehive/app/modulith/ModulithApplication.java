package tn.eniso.computehive.app.modulith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import tn.eniso.computehive.drivers.brokermemory.MemoryBrokerConfiguration;
import tn.eniso.computehive.drivers.notiflog.LogNotifierConfiguration;

/**
 * ComputeHive en M/1 : tous les modules dans un seul processus (modulith).
 *
 * <p>L'assemblage tient dans cette classe et dans le {@code pom.xml} :</p>
 * <ul>
 *   <li><b>modules</b> : le core et les quatre modules sont trouvés par scan de leurs paquetages ; chaque façade
 *       est son implémentation locale ({@code service-impl}), sans proxy distant ;</li>
 *   <li><b>drivers</b> : importés explicitement, ce sont les seuls choix techniques de l'application — broker en
 *       mémoire et notifications dans les logs. En M/P ({@code app-main}, {@code app-scheduling}), les mêmes modules
 *       sont assemblés avec le driver Kafka et le driver e-mail, sans changer leur code.</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
        "tn.eniso.computehive.core",
        "tn.eniso.computehive.modules"
})
@Import({
        MemoryBrokerConfiguration.class,
        LogNotifierConfiguration.class
})
public class ModulithApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModulithApplication.class, args);
    }
}
