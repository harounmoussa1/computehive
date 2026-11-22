package tn.eniso.computehive.tests.mp;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Une application ComputeHive lancée comme en production : {@code java -jar}, dans son propre processus, avec sa
 * configuration en arguments. Sa sortie est écrite dans {@code target/mp-logs/<nom>.log}, et citée si le démarrage
 * échoue.
 */
final class ApplicationProcess implements AutoCloseable {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    private final String name;
    private final int httpPort;
    private final Path log;
    private final Process process;

    private ApplicationProcess(String name, int httpPort, Path log, Process process) {
        this.name = name;
        this.httpPort = httpPort;
        this.log = log;
        this.process = process;
    }

    /** Lance {@code <apps.dir>/<name>.jar} avec {@code arguments} (forme {@code --propriété=valeur}). */
    static ApplicationProcess start(String name, int httpPort, List<String> arguments) throws IOException {
        Path jar = Path.of(System.getProperty("computehive.apps.dir", "target/apps")).resolve(name + ".jar");
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException(jar + " not found: run the build up to 'verify' (mvnw verify)");
        }
        Path logs = Files.createDirectories(Path.of(System.getProperty("computehive.logs.dir", "target/mp-logs")));
        Path log = logs.resolve(name + ".log");
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Xmx512m", "-jar", jar.toString(),
                "--server.port=" + httpPort));
        command.addAll(arguments);
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        return new ApplicationProcess(name, httpPort, log, process);
    }

    String baseUrl() {
        return "http://localhost:" + httpPort;
    }

    /** Attend que {@code /actuator/health} réponde 200 ; échoue tout de suite si le processus s'arrête. */
    void awaitHealthy(Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        HttpRequest health = HttpRequest.newBuilder(URI.create(baseUrl() + "/actuator/health"))
                .timeout(Duration.ofSeconds(2)).build();
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException(name + " stopped with exit code " + process.exitValue()
                        + ":\n" + tailOfLog());
            }
            try {
                if (HTTP.send(health, HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                    return;
                }
            } catch (ConnectException | java.net.http.HttpTimeoutException e) {
                // pas encore à l'écoute
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException(name + " not healthy within " + timeout + ":\n" + tailOfLog());
    }

    private String tailOfLog() throws IOException {
        List<String> lines = Files.readAllLines(log);
        return String.join(System.lineSeparator(), lines.subList(Math.max(0, lines.size() - 40), lines.size()));
    }

    /** Arrêt propre (SIGTERM, comme {@code docker stop}), puis forcé s'il traîne. */
    @Override
    public void close() throws InterruptedException {
        process.destroy();
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
        }
    }
}
