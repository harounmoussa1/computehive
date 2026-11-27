package tn.eniso.computehive.drivers.storageremote;

import org.junit.jupiter.api.Test;
import tn.eniso.computehive.drivers.storageremote.RemoteBucket.ObjectInfo;
import tn.eniso.computehive.drivers.storageremote.RemoteBucket.RemoteUnavailableException;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointKey;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointStorageException;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Un service S3 injoignable : erreur réseau traduite pour Resilience4j, sans Docker. */
class S3RemoteBucketTest {

    /** Port 1 : rien n'y écoute, la connexion est refusée tout de suite. */
    private final S3RemoteBucket unreachable =
            new S3RemoteBucket("http://127.0.0.1:1", "access", "secret", "computehive");

    @Test
    void anUnreachableServiceIsReportedAsUnavailable() throws Exception {
        Path file = Files.createTempFile("checkpoint", ".bin");
        try {
            assertThatThrownBy(() -> unreachable.put("j-1/attempts/1/epochs/1", file,
                    new ObjectInfo("0".repeat(64), 0, Instant.now())))
                    .isInstanceOf(RemoteUnavailableException.class);
            assertThatThrownBy(() -> unreachable.stat("j-1/attempts/1/epochs/1"))
                    .isInstanceOf(RemoteUnavailableException.class);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void theStoreRetriesThenReportsAStorageFailure() {
        RemoteStorageProperties fast = new RemoteStorageProperties(RemoteStorageProperties.defaults().s3(),
                Duration.ZERO, 0.0, 2, Duration.ofMillis(10), 10, 50, Duration.ofSeconds(1));
        RemoteCheckpointStore store = new RemoteCheckpointStore(unreachable, fast, Clock.systemUTC());

        assertThatThrownBy(() -> store.store(new CheckpointKey("j-1", 1, 1), new ByteArrayInputStream(new byte[]{1})))
                .isInstanceOf(CheckpointStorageException.class)
                .hasMessageContaining("after 2 attempts");
    }
}
