package tn.eniso.computehive.drivers.storageremote;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.util.Sha256;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointKey;
import tn.eniso.computehive.extensions.checkpoints.api.StoredCheckpoint;
import tn.eniso.computehive.extensions.files.api.FileKey;
import tn.eniso.computehive.extensions.files.api.StoredFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.util.Arrays;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le driver (checkpoints et fichiers) sur un vrai service compatible S3 : SeaweedFS (Testcontainers), avec la même image et la même
 * configuration d'accès que docker-compose.yml. Ignoré si Docker n'est pas disponible.
 */
@Testcontainers(disabledWithoutDocker = true)
class S3CheckpointStoreIT {

    private static final String ACCESS_KEY = "computehive-test";
    private static final String SECRET_KEY = "computehive-test-secret";
    private static final int S3_PORT = 8333;

    @Container
    static final GenericContainer<?> SEAWEEDFS = new GenericContainer<>("chrislusf/seaweedfs:3.80")
            .withCommand("server", "-dir=/data", "-s3", "-s3.config=/etc/seaweedfs/s3.json")
            .withCopyToContainer(Transferable.of("""
                    {"identities": [{"name": "computehive",
                      "credentials": [{"accessKey": "%s", "secretKey": "%s"}],
                      "actions": ["Admin", "Read", "Write", "List", "Tagging"]}]}
                    """.formatted(ACCESS_KEY, SECRET_KEY)), "/etc/seaweedfs/s3.json")
            .withExposedPorts(S3_PORT)
            // Prêt quand l'API S3 répond, même par un refus (403 sans identifiants) : plus d'erreur 5xx.
            .waitingFor(Wait.forHttp("/").forPort(S3_PORT).forStatusCodeMatching(code -> code < 500));

    private static S3RemoteBucket bucket() {
        String endpoint = "http://" + SEAWEEDFS.getHost() + ":" + SEAWEEDFS.getMappedPort(S3_PORT);
        return new S3RemoteBucket(endpoint, ACCESS_KEY, SECRET_KEY, "computehive");
    }

    private static RemoteCheckpointStore newStore() {
        return new RemoteCheckpointStore(bucket(), RemoteStorageProperties.defaults(), Clock.systemUTC());
    }

    private static RemoteFileStore newFileStore() {
        return new RemoteFileStore(bucket(), new ResilientStorage(RemoteStorageProperties.defaults()),
                Clock.systemUTC());
    }

    @Test
    void aUserFileIsStoredOnceUnderItsDigestAndReadFromAnOffset() throws Exception {
        RemoteFileStore store = newFileStore();
        byte[] dataset = randomBytes(50_000);

        StoredFile first = store.store(new ByteArrayInputStream(dataset));
        StoredFile again = newFileStore().store(new ByteArrayInputStream(dataset));

        assertThat(first.sha256()).isEqualTo(Sha256.of(dataset));
        assertThat(again).as("same content: same file, not sent twice").isEqualTo(first);
        assertThat(store.find(first.key())).contains(first);
        try (InputStream rest = store.open(first.key(), 40_000)) {
            assertThat(rest.readAllBytes()).isEqualTo(Arrays.copyOfRange(dataset, 40_000, dataset.length));
        }
        assertThat(store.find(new FileKey("0".repeat(64)))).isEmpty();
    }

    @Test
    void aCheckpointIsStoredReadBackAndDescribedWithoutRereadingIt() throws Exception {
        RemoteCheckpointStore store = newStore();
        byte[] weights = randomBytes(1_000);
        CheckpointKey key = new CheckpointKey("job-1", 1, 3);

        StoredCheckpoint stored = store.store(key, new ByteArrayInputStream(weights));

        assertThat(stored.sha256()).isEqualTo(Sha256.of(weights));
        assertThat(stored.sizeBytes()).isEqualTo(weights.length);
        assertThat(store.find(key)).contains(stored);
        try (InputStream in = store.open(key)) {
            assertThat(in.readAllBytes()).isEqualTo(weights);
        }
    }

    @Test
    void aMissingCheckpointIsAbsentNotAnError() {
        RemoteCheckpointStore store = newStore();
        CheckpointKey missing = new CheckpointKey("job-404", 1, 1);

        assertThat(store.find(missing)).isEmpty();
        assertThatThrownBy(() -> store.open(missing)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void checkpointsSurviveARestartOfTheApplication() {
        CheckpointKey key = new CheckpointKey("job-2", 2, 5);
        StoredCheckpoint stored = newStore().store(key, new ByteArrayInputStream(randomBytes(100)));

        RemoteCheckpointStore afterRestart = newStore();

        assertThat(afterRestart.find(key)).contains(stored);
    }

    @Test
    void aLargeFileIsUploadedInPartsAndReadBackIntact() throws Exception {
        RemoteCheckpointStore store = newStore();
        byte[] large = randomBytes(12 * 1024 * 1024);
        CheckpointKey key = new CheckpointKey("job-3", 1, 1);

        StoredCheckpoint stored = store.store(key, new ByteArrayInputStream(large));

        try (InputStream in = store.open(key)) {
            assertThat(Sha256.of(in.readAllBytes())).isEqualTo(stored.sha256()).isEqualTo(Sha256.of(large));
        }
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new Random(size).nextBytes(bytes);
        return bytes;
    }
}
