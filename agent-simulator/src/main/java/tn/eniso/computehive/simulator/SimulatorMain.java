package tn.eniso.computehive.simulator;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Lance un ou plusieurs faux agents ; ils tournent jusqu'à l'arrêt du processus (Ctrl+C).
 *
 * <pre>
 * java -jar agent-simulator/target/agent-simulator.jar --server=localhost:9090 --count=3
 * </pre>
 */
public final class SimulatorMain {

    private SimulatorMain() {
    }

    public static void main(String[] args) throws InterruptedException {
        if (Arrays.asList(args).contains("--help")) {
            new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8)
                    .println(SimulatorOptions.USAGE);
            return;
        }
        SimulatorOptions options;
        try {
            options = SimulatorOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(SimulatorOptions.USAGE);
            System.exit(2);
            return;
        }

        // Un seul canal pour tous les agents : chacun ouvre son propre stream dessus.
        // Pings réguliers : une coupure du réseau est détectée même sans message à envoyer.
        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(options.host(), options.port())
                .usePlaintext()
                .keepAliveTime(20, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true);
        if (options.token() != null) {
            // Jeton partagé des agents : envoyé à l'ouverture de chaque stream (metadata gRPC).
            Metadata credentials = new Metadata();
            credentials.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                    "Bearer " + options.token());
            builder.intercept(MetadataUtils.newAttachHeadersInterceptor(credentials));
        }
        ManagedChannel channel = builder.build();
        CheckpointClient checkpoints = options.storageUrl() == null
                ? null
                : new CheckpointClient(options.storageUrl(), options.token());
        List<SimulatedAgent> agents = options.agents().stream()
                .map(profile -> new SimulatedAgent(channel, profile, options.behaviour(), checkpoints))
                .toList();
        agents.forEach(SimulatedAgent::start);

        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            agents.forEach(SimulatedAgent::close);
            channel.shutdown();
            stopped.countDown();
        }, "simulator-shutdown"));
        stopped.await();
    }
}
