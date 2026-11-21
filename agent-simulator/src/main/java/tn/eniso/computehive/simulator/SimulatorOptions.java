package tn.eniso.computehive.simulator;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Options de la ligne de commande du faux agent.
 *
 * @param host      adresse du serveur
 * @param port      port gRPC du serveur
 * @param agents    PC simulés (un faux agent chacun)
 * @param behaviour comportement commun à tous les PC simulés
 */
record SimulatorOptions(String host, int port, List<AgentProfile> agents, Behaviour behaviour) {

    static final String USAGE = """
            Faux agent ComputeHive : simule un ou plusieurs PC workers, en ne parlant que worker.proto.

            java -jar agent-simulator.jar [options]
              --server=HOTE:PORT     serveur gRPC (défaut : localhost:9090)
              --count=N              nombre de PC simulés (défaut : 1)
              --agent-id=PREFIXE     identifiants PREFIXE-1, PREFIXE-2… (défaut : sim)
              --cpu                  workers CPU, sans GPU
              --gpu-name=NOM         nom du GPU simulé (défaut : NVIDIA GeForce RTX 3060)
              --gpu-memory-mb=MO     mémoire du GPU simulé (défaut : 12288)
              --epoch-ms=MS          durée d'une epoch simulée (défaut : 1000)
              --checkpoint-every=N   un checkpoint toutes les N epochs, 0 pour aucun (défaut : 2)
              --reject-launches      refuser tous les jobs (CommandAck refusé)
              --fail-at-epoch=N      le script échoue à l'epoch N (Finished FAILED)
              --crash-at-epoch=N     le PC « meurt » à l'epoch N : plus de heartbeat, stream coupé
              --help                 afficher cette aide
            """;

    /** @throws IllegalArgumentException si une option est inconnue ou invalide */
    static SimulatorOptions parse(String... args) {
        String server = "localhost:9090";
        int count = 1;
        String prefix = "sim";
        boolean cpu = false;
        String gpuName = "NVIDIA GeForce RTX 3060";
        long gpuMemoryMb = 12_288;
        Behaviour behaviour = Behaviour.normal();
        for (String arg : args) {
            String name = arg.contains("=") ? arg.substring(0, arg.indexOf('=')) : arg;
            String value = arg.contains("=") ? arg.substring(arg.indexOf('=') + 1) : "";
            switch (name) {
                case "--server" -> server = value;
                case "--count" -> count = positive(name, value);
                case "--agent-id" -> prefix = required(name, value);
                case "--cpu" -> cpu = true;
                case "--gpu-name" -> gpuName = required(name, value);
                case "--gpu-memory-mb" -> gpuMemoryMb = positive(name, value);
                case "--epoch-ms" -> behaviour = behaviour.withEpochDuration(Duration.ofMillis(positive(name, value)));
                case "--checkpoint-every" -> behaviour = behaviour.withCheckpointEvery(number(name, value));
                case "--reject-launches" -> behaviour = behaviour.rejectingLaunches();
                case "--fail-at-epoch" -> behaviour = behaviour.failingAt(positive(name, value));
                case "--crash-at-epoch" -> behaviour = behaviour.crashingAt(positive(name, value));
                default -> throw new IllegalArgumentException("unknown option: " + arg);
            }
        }
        int colon = server.lastIndexOf(':');
        if (colon <= 0) {
            throw new IllegalArgumentException("--server must look like HOST:PORT: " + server);
        }
        List<AgentProfile> agents = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String agentId = prefix + "-" + i;
            agents.add(cpu ? AgentProfile.cpu(agentId) : AgentProfile.gpu(agentId, gpuName, gpuMemoryMb));
        }
        return new SimulatorOptions(server.substring(0, colon), positive("--server port", server.substring(colon + 1)),
                List.copyOf(agents), behaviour);
    }

    private static String required(String name, String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " needs a value");
        }
        return value;
    }

    private static int number(String name, String value) {
        try {
            int number = Integer.parseInt(value);
            if (number < 0) {
                throw new IllegalArgumentException(name + " must not be negative");
            }
            return number;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a number: " + value);
        }
    }

    private static int positive(String name, String value) {
        int number = number(name, value);
        if (number == 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return number;
    }
}
