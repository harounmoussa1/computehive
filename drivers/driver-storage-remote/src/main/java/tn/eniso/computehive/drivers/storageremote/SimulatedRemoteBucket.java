package tn.eniso.computehive.drivers.storageremote;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stockage objet distant <b>simulé</b>, pour les tests : il garde les objets en mémoire, et se comporte comme un
 * service réseau capricieux, pour exercer la résilience du driver sans lancer de vrai service.
 *
 * <ul>
 *   <li>{@code latency} : délai ajouté à chaque appel ;</li>
 *   <li>{@code failureRate} : proportion d'appels qui échouent au hasard (panne passagère) ;</li>
 *   <li>{@link #setAvailable(boolean)} : coupure franche du service.</li>
 * </ul>
 *
 * <p>Les objets sont perdus à l'arrêt : en déploiement, c'est {@link S3RemoteBucket} qui est utilisé.</p>
 */
public class SimulatedRemoteBucket implements RemoteBucket {

    private record StoredObject(byte[] content, ObjectInfo info) {
    }

    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
    private final Duration latency;
    private final double failureRate;
    private final Random random;
    private volatile boolean available = true;

    public SimulatedRemoteBucket(Duration latency, double failureRate, long seed) {
        if (failureRate < 0 || failureRate > 1) {
            throw new IllegalArgumentException("failureRate must be between 0 and 1");
        }
        this.latency = latency;
        this.failureRate = failureRate;
        this.random = new Random(seed);
    }

    @Override
    public void put(String key, Path file, ObjectInfo info) {
        call();
        try {
            objects.put(key, new StoredObject(Files.readAllBytes(file), info));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<ObjectInfo> stat(String key) {
        call();
        return Optional.ofNullable(objects.get(key)).map(StoredObject::info);
    }

    @Override
    public Optional<InputStream> open(String key) {
        call();
        return Optional.ofNullable(objects.get(key)).map(object -> new ByteArrayInputStream(object.content()));
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    private void call() {
        if (!latency.isZero()) {
            try {
                Thread.sleep(latency.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RemoteUnavailableException("interrupted");
            }
        }
        boolean fails;
        synchronized (random) {
            fails = random.nextDouble() < failureRate;
        }
        if (!available || fails) {
            throw new RemoteUnavailableException(available ? "simulated transient failure" : "simulated outage");
        }
    }
}
