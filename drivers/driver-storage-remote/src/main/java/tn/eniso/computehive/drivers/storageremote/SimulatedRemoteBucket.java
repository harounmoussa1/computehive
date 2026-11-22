package tn.eniso.computehive.drivers.storageremote;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stockage objet distant <b>simulé</b> (aucun service payant) : il garde les objets en mémoire, et se comporte
 * comme un service réseau capricieux, pour exercer la résilience du driver.
 *
 * <ul>
 *   <li>{@code latency} : délai ajouté à chaque appel ;</li>
 *   <li>{@code failureRate} : proportion d'appels qui échouent au hasard (panne passagère) ;</li>
 *   <li>{@link #setAvailable(boolean)} : coupure franche du service, pour la démo et les tests.</li>
 * </ul>
 */
public class SimulatedRemoteBucket {

    /** Objet stocké : contenu et métadonnées. */
    public record RemoteObject(byte[] content, String sha256, Instant storedAt) {
    }

    /** Le service distant n'a pas répondu (panne simulée). */
    public static class RemoteUnavailableException extends RuntimeException {
        public RemoteUnavailableException(String message) {
            super(message);
        }
    }

    private final Map<String, RemoteObject> objects = new ConcurrentHashMap<>();
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

    public void put(String key, RemoteObject object) {
        call();
        objects.put(key, object);
    }

    public Optional<RemoteObject> get(String key) {
        call();
        return Optional.ofNullable(objects.get(key));
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
