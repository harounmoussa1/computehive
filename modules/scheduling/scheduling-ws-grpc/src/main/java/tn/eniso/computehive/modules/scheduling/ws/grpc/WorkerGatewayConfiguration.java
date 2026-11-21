package tn.eniso.computehive.modules.scheduling.ws.grpc;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Active la porte gRPC des agents : le service {@code WorkerGateway}, le registre des sessions
 * (qui fournit {@code AgentChannel} au module scheduling) et le serveur. À importer par l'application
 * qui embarque scheduling ({@code app-modulith}, {@code app-scheduling}).
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = WorkerGatewayConfiguration.class)
@EnableConfigurationProperties(GrpcServerProperties.class)
public class WorkerGatewayConfiguration {

    @Bean
    WorkerGatewayServer workerGatewayServer(WorkerGatewayService gateway, GrpcServerProperties properties) {
        return new WorkerGatewayServer(gateway, properties);
    }
}
