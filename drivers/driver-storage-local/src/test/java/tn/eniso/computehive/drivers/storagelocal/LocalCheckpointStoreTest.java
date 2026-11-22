package tn.eniso.computehive.drivers.storagelocal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointKey;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointStorageException;
import tn.eniso.computehive.extensions.checkpoints.api.Sha256;
import tn.eniso.computehive.extensions.checkpoints.api.StoredCheckpoint;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalCheckpointStoreTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC);
    private static final CheckpointKey KEY = new CheckpointKey("j-7", 1, 4);

    @TempDir
    Path root;

    private LocalCheckpointStore store() {
        return new LocalCheckpointStore(root, CLOCK);
    }

    private static InputStream content(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void aCheckpointIsStoredWithItsDigestAndReadBack() throws IOException {
        StoredCheckpoint stored = store().store(KEY, content("weights after epoch 4"));

        assertThat(stored.sha256()).isEqualTo(Sha256.of("weights after epoch 4".getBytes(StandardCharsets.UTF_8)));
        assertThat(stored.sizeBytes()).isEqualTo(21);
        assertThat(stored.storedAt()).isEqualTo(CLOCK.instant());
        assertThat(store().find(KEY)).contains(stored);
        try (InputStream in = store().open(KEY)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("weights after epoch 4");
        }
        assertThat(root.resolve("j-7/attempts/1/epochs/4")).exists();
    }

    @Test
    void aNewUploadUnderTheSameKeyReplacesThePreviousOne() {
        store().store(KEY, content("first, interrupted"));
        StoredCheckpoint second = store().store(KEY, content("second, complete"));

        assertThat(store().find(KEY)).contains(second);
    }

    @Test
    void attemptsNeverShareACheckpoint() {
        store().store(new CheckpointKey("j-7", 1, 4), content("stale attempt"));
        StoredCheckpoint current = store().store(new CheckpointKey("j-7", 2, 4), content("current attempt"));

        assertThat(store().find(new CheckpointKey("j-7", 2, 4))).contains(current);
        assertThat(store().find(new CheckpointKey("j-7", 1, 4)).orElseThrow().sha256()).isNotEqualTo(current.sha256());
    }

    @Test
    void aMissingCheckpointIsNotFound() {
        assertThat(store().find(KEY)).isEmpty();
        assertThatThrownBy(() -> store().open(KEY)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void anInterruptedUploadLeavesNoVisibleCheckpoint() {
        InputStream broken = new InputStream() {
            private int sent;

            @Override
            public int read() throws IOException {
                if (sent++ < 100) {
                    return 'x';
                }
                throw new IOException("connection reset by the agent");
            }
        };

        assertThatThrownBy(() -> store().store(KEY, broken)).isInstanceOf(CheckpointStorageException.class);
        assertThat(store().find(KEY)).isEmpty();
        assertThat(Files.exists(root.resolve("j-7/attempts/1/epochs/4"))).isFalse();
    }
}
