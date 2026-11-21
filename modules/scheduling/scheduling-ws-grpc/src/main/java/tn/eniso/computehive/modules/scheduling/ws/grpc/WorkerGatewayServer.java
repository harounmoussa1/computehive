package tn.eniso.computehive.modules.scheduling.ws.grpc;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;

/**
 * Serveur gRPC des agents, démarré et arrêté avec l'application (Spring {@link SmartLifecycle}).
 *
 * <p>Les streams des agents restent ouverts des heures : le serveur envoie des pings sur un stream inactif
 * pour détecter un agent disparu, et accepte ceux des agents (sans quoi il fermerait leur connexion).</p>
 */
public class WorkerGatewayServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(WorkerGatewayServer.class);

    private final WorkerGatewayService gateway;
    private final GrpcServerProperties properties;
    private Server server;

    public WorkerGatewayServer(WorkerGatewayService gateway, GrpcServerProperties properties) {
        this.gateway = gateway;
        this.properties = properties;
    }

    @Override
    public synchronized void start() {
        try {
            server = NettyServerBuilder.forPort(properties.port())
                    .addService(gateway)
                    .keepAliveTime(properties.keepAliveTime().toMillis(), TimeUnit.MILLISECONDS)
                    .keepAliveTimeout(properties.keepAliveTimeout().toMillis(), TimeUnit.MILLISECONDS)
                    .permitKeepAliveTime(properties.permitKeepAliveTime().toMillis(), TimeUnit.MILLISECONDS)
                    .permitKeepAliveWithoutCalls(true)
                    .build()
                    .start();
        } catch (IOException e) {
            throw new UncheckedIOException("gRPC server could not listen on port " + properties.port(), e);
        }
        log.info("WorkerGateway listening on port {}", server.getPort());
    }

    @Override
    public synchronized void stop() {
        if (server == null) {
            return;
        }
        server.shutdown();
        try {
            if (!server.awaitTermination(properties.shutdownGracePeriod().toMillis(), TimeUnit.MILLISECONDS)) {
                server.shutdownNow();
            }
        } catch (InterruptedException e) {
            server.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("WorkerGateway stopped");
        server = null;
    }

    @Override
    public synchronized boolean isRunning() {
        return server != null && !server.isShutdown();
    }

    /** Port réellement utilisé (utile avec le port {@code 0}). */
    public synchronized int port() {
        return server == null ? -1 : server.getPort();
    }
}
