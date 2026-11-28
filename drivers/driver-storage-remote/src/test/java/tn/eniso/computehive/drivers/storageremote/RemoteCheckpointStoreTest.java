package tn.eniso.computehive.drivers.storageremote;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.drivers.storageremote.RemoteBucket.ObjectInfo;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointKey;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointStorageException;
import tn.eniso.computehive.core.infra.util.Sha256;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Le stockage distant (simulé ici) et sa protection Resilience4j : retry, ouverture du circuit, retour à la normale. */
class RemoteCheckpointStoreTest {

    private static final CheckpointKey KEY = new CheckpointKey("j-7", 1, 4);
    private static final RemoteStorageProperties FAST = new RemoteStorageProperties(
            RemoteStorageProperties.defaults().s3(), Duration.ZERO, 0.0, 3, Duration.ofMillis(10), 10, 50,
            Duration.ofMillis(300));

    private static InputStream content(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Bucket qui échoue sur ses {@code failures} premiers appels, puis répond normalement. */
    private static final class FlakyBucket extends SimulatedRemoteBucket {

        private final AtomicInteger calls = new AtomicInteger();
        private final int failures;

        FlakyBucket(int failures) {
            super(Duration.ZERO, 0.0, 1);
            this.failures = failures;
        }

        @Override
        public void put(String key, Path file, ObjectInfo info) {
            failFirstCalls();
            super.put(key, file, info);
        }

        @Override
        public Optional<ObjectInfo> stat(String key) {
            failFirstCalls();
            return super.stat(key);
        }

        private void failFirstCalls() {
            if (calls.incrementAndGet() <= failures) {
                throw new RemoteUnavailableException("transient failure " + calls.get());
            }
        }
    }

    @Test
    void aCheckpointIsStoredRemotelyAndReadBack() throws Exception {
        RemoteCheckpointStore store = new RemoteCheckpointStore(new SimulatedRemoteBucket(Duration.ZERO, 0, 1), FAST,
                Clock.systemUTC());

        var stored = store.store(KEY, content("weights"));

        assertThat(stored.sha256()).isEqualTo(Sha256.of("weights".getBytes(StandardCharsets.UTF_8)));
        assertThat(store.find(KEY)).contains(stored);
        try (InputStream in = store.open(KEY)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("weights");
        }
        assertThatThrownBy(() -> store.open(new CheckpointKey("j-7", 1, 5))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aTransientFailureIsRetriedTransparently() {
        FlakyBucket bucket = new FlakyBucket(2);
        RemoteCheckpointStore store = new RemoteCheckpointStore(bucket, FAST, Clock.systemUTC());

        store.store(KEY, content("weights"));

        assertThat(bucket.calls).as("2 failures, then success on the 3rd attempt").hasValue(3);
        assertThat(store.circuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void aFailurePersistingAfterAllAttemptsIsReported() {
        RemoteCheckpointStore store = new RemoteCheckpointStore(new FlakyBucket(100), FAST, Clock.systemUTC());

        assertThatThrownBy(() -> store.store(KEY, content("weights")))
                .isInstanceOf(CheckpointStorageException.class)
                .hasMessageContaining("after 3 attempts");
    }

    @Test
    void anOutageOpensTheCircuitWhichThenFailsFastAndRecoversWhenTheServiceIsBack() throws Exception {
        SimulatedRemoteBucket bucket = new SimulatedRemoteBucket(Duration.ZERO, 0, 1);
        RemoteCheckpointStore store = new RemoteCheckpointStore(bucket, FAST, Clock.systemUTC());
        store.store(KEY, content("weights"));
        bucket.setAvailable(false);

        for (int i = 0; i < 2 && store.circuitState() != CircuitBreaker.State.OPEN; i++) {
            assertThatThrownBy(() -> store.find(KEY)).isInstanceOf(CheckpointStorageException.class);
        }
        assertThat(store.circuitState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> store.find(KEY))
                .isInstanceOf(CheckpointStorageException.class)
                .hasMessageContaining("circuit open");

        bucket.setAvailable(true);
        Thread.sleep(FAST.circuitOpenDuration().toMillis() + 100);

        assertThat(store.find(KEY)).isPresent();
        assertThat(store.find(KEY)).isPresent();
        assertThat(store.circuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void theCircuitStateAndTheRetriesArePublishedAsMetrics() {
        MeterRegistry meters = new SimpleMeterRegistry();
        RemoteCheckpointStore store = new RemoteCheckpointStore(new FlakyBucket(2), FAST, Clock.systemUTC());
        store.bindTo(meters);

        store.store(KEY, content("weights"));

        assertThat(meters.get("resilience4j.circuitbreaker.state")
                .tags("name", "object-storage", "state", "closed").gauge().value()).isEqualTo(1);
        assertThat(meters.get("resilience4j.retry.calls")
                .tags("name", "object-storage", "kind", "successful_with_retry").functionCounter().count())
                .isEqualTo(1);
    }

    @Test
    void theTemporaryCopyOfTheContentIsDeletedAfterTheUpload() {
        Path[] uploaded = new Path[1];
        SimulatedRemoteBucket bucket = new SimulatedRemoteBucket(Duration.ZERO, 0, 1) {
            @Override
            public void put(String key, Path file, ObjectInfo info) {
                uploaded[0] = file;
                super.put(key, file, info);
            }
        };
        RemoteCheckpointStore store = new RemoteCheckpointStore(bucket, FAST, Clock.systemUTC());

        store.store(KEY, content("weights"));

        assertThat(uploaded[0]).isNotNull().doesNotExist();
    }
}
