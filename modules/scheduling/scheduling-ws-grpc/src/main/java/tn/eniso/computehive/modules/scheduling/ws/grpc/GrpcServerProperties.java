package tn.eniso.computehive.modules.scheduling.ws.grpc;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Réglages du serveur gRPC des agents ({@code computehive.grpc.*}).
 *
 * @param port                  port d'écoute ({@code 0} : port libre choisi par le système, pour les tests)
 * @param keepAliveTime         fréquence des pings du serveur sur un stream inactif, pour détecter un agent disparu
 * @param keepAliveTimeout      délai de réponse à un ping avant de fermer la connexion
 * @param permitKeepAliveTime   fréquence minimale des pings acceptée de la part des agents
 * @param shutdownGracePeriod   délai laissé aux streams pour se fermer à l'arrêt du serveur
 */
@ConfigurationProperties("computehive.grpc")
public record GrpcServerProperties(
        @DefaultValue("9090") int port,
        @DefaultValue("30s") Duration keepAliveTime,
        @DefaultValue("10s") Duration keepAliveTimeout,
        @DefaultValue("10s") Duration permitKeepAliveTime,
        @DefaultValue("5s") Duration shutdownGracePeriod) {

    public GrpcServerProperties {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("computehive.grpc.port must be between 0 and 65535");
        }
    }

    public static GrpcServerProperties onPort(int port) {
        return new GrpcServerProperties(port, Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ofSeconds(10),
                Duration.ofSeconds(5));
    }
}
