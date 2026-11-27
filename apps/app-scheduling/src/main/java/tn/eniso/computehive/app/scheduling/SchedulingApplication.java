package tn.eniso.computehive.app.scheduling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Import;
import tn.eniso.computehive.drivers.brokerkafka.KafkaBrokerConfiguration;
import tn.eniso.computehive.drivers.storageremote.RemoteCheckpointStoreConfiguration;

/**
 * ComputeHive en M/P, processus extrait : scheduling et tout ce qui parle aux agents.
 *
 * <ul>
 *   <li><b>modules</b> : trouvés par scan, comme en M/1. scheduling y est en {@code service-impl} ; jobs, machines et
 *       quotas n'y sont qu'en {@code service-rest-cli}, leurs proxys vers {@code app-main}
 *       ({@code computehive.remote.*.url}) ;</li>
 *   <li><b>agents</b> : la porte gRPC ({@code scheduling-ws-grpc}) et l'API des checkpoints
 *       ({@code checkpoint-storage-ws-rest}) sont ici, avec {@code scheduling-service-impl} : les ordres aux agents
 *       repartent par les streams ouverts dans ce processus, et le checkpoint qu'un agent dépose est vérifié par
 *       scheduling avant de devenir officiel ;</li>
 *   <li><b>drivers</b> : Kafka (événements venus d'{@code app-main}) et le stockage S3 des checkpoints
 *       (SeaweedFS, ADR 0015).</li>
 * </ul>
 */
@SpringBootApplication(
        scanBasePackages = {
                "tn.eniso.computehive.core",
                "tn.eniso.computehive.modules",
                "tn.eniso.computehive.extensions.checkpoints.ws"
        },
        exclude = KafkaAutoConfiguration.class)
@Import({KafkaBrokerConfiguration.class, RemoteCheckpointStoreConfiguration.class})
public class SchedulingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulingApplication.class, args);
    }
}
