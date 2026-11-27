package tn.eniso.computehive.drivers.storagelocal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.util.Sha256;
import tn.eniso.computehive.extensions.files.api.FileKey;
import tn.eniso.computehive.extensions.files.api.StoredFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStoreTest {

    private static final Instant NOW = Instant.parse("2026-11-28T10:00:00Z");
    private static final byte[] DATASET = "images and labels".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path root;

    private LocalFileStore store() {
        return new LocalFileStore(root, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aFileIsStoredUnderItsDigestAndReadBack() throws Exception {
        StoredFile stored = store().store(new ByteArrayInputStream(DATASET));

        assertThat(stored.sha256()).isEqualTo(Sha256.of(DATASET));
        assertThat(stored.sizeBytes()).isEqualTo(DATASET.length);
        assertThat(stored.storedAt()).isEqualTo(NOW);
        assertThat(root.resolve("files").resolve(stored.sha256())).exists();
        assertThat(store().find(stored.key())).contains(stored);
        try (InputStream in = store().open(stored.key(), 0)) {
            assertThat(in.readAllBytes()).isEqualTo(DATASET);
        }
    }

    @Test
    void aDownloadResumesFromAnOffset() throws Exception {
        StoredFile stored = store().store(new ByteArrayInputStream(DATASET));

        try (InputStream rest = store().open(stored.key(), 7)) {
            assertThat(new String(rest.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("and labels");
        }
    }

    @Test
    void theSameContentIsStoredOnceAndNoTemporaryFileIsLeft() throws Exception {
        StoredFile first = store().store(new ByteArrayInputStream(DATASET));
        StoredFile again = store().store(new ByteArrayInputStream(DATASET));

        assertThat(again).isEqualTo(first);
        try (var incoming = Files.list(root.resolve(LocalFileStore.INCOMING))) {
            assertThat(incoming).isEmpty();
        }
    }

    @Test
    void anUnknownFileIsAbsent() {
        FileKey unknown = new FileKey("0".repeat(64));

        assertThat(store().find(unknown)).isEmpty();
        assertThatThrownBy(() -> store().open(unknown, 0)).isInstanceOf(NotFoundException.class);
    }
}
