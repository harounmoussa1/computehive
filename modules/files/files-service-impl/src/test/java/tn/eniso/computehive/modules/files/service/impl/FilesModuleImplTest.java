package tn.eniso.computehive.modules.files.service.impl;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.util.unit.DataSize;
import tn.eniso.computehive.core.infra.exception.ConflictException;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.core.infra.util.Sha256;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorDispatcher;
import tn.eniso.computehive.extensions.files.api.FileKey;
import tn.eniso.computehive.extensions.files.api.FileStore;
import tn.eniso.computehive.extensions.files.api.StoredFile;
import tn.eniso.computehive.modules.files.dal.api.UserFileRepository;
import tn.eniso.computehive.modules.files.infra.FileInfo;
import tn.eniso.computehive.modules.files.infra.FileKind;
import tn.eniso.computehive.modules.files.infra.UserFile;
import tn.eniso.computehive.modules.files.service.impl.entity.UserFileCrudService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilesModuleImplTest {

    private static final Instant NOW = Instant.parse("2026-11-28T10:00:00Z");
    private static final UserRef ALICE = new UserRef("u-1", "Alice", "MEMBER");
    private static final UserRef BOB = new UserRef("u-2", "Bob", "MEMBER");

    /** Faux repository : même contrat que JPA (nom unique par propriétaire et par type). */
    private static final class InMemoryUserFileRepository implements UserFileRepository {

        final Map<String, UserFile> rows = new LinkedHashMap<>();

        @Override
        public UserFile save(UserFile file) {
            boolean taken = rows.values().stream().anyMatch(other -> !other.getId().equals(file.getId())
                    && other.getOwner().id().equals(file.getOwner().id())
                    && other.getKind() == file.getKind() && other.getName().equals(file.getName()));
            if (taken) {
                throw new DataIntegrityViolationException("duplicate name");
            }
            UserFile copy = file.copy();
            copy.setVersion(Objects.requireNonNullElse(file.getVersion(), -1L) + 1);
            rows.put(copy.getId(), copy);
            return copy.copy();
        }

        @Override
        public Optional<UserFile> findById(String id) {
            return Optional.ofNullable(rows.get(id)).map(UserFile::copy);
        }

        @Override
        public Optional<UserFile> findByOwnerKindAndName(String ownerId, FileKind kind, String name) {
            return rows.values().stream().filter(f -> f.isOwnedBy(ownerId) && f.getKind() == kind
                    && f.getName().equals(name)).findFirst().map(UserFile::copy);
        }

        @Override
        public List<UserFile> findByOwner(String ownerId, FileKind kind) {
            return rows.values().stream()
                    .filter(f -> f.isOwnedBy(ownerId) && (kind == null || f.getKind() == kind))
                    .sorted(Comparator.comparing(UserFile::getKind).thenComparing(UserFile::getName))
                    .map(UserFile::copy).toList();
        }

        @Override
        public void delete(String id) {
            rows.remove(id);
        }
    }

    /** Faux stockage : compte les contenus réellement stockés. */
    private static final class MemoryFileStore implements FileStore {

        final Map<FileKey, byte[]> files = new HashMap<>();

        @Override
        public StoredFile store(InputStream content) {
            try {
                byte[] bytes = content.readAllBytes();
                FileKey key = new FileKey(Sha256.of(bytes));
                files.putIfAbsent(key, bytes);
                return new StoredFile(key, bytes.length, NOW);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public Optional<StoredFile> find(FileKey key) {
            return Optional.ofNullable(files.get(key)).map(b -> new StoredFile(key, b.length, NOW));
        }

        @Override
        public InputStream open(FileKey key, long offset) {
            byte[] bytes = files.get(key);
            return new ByteArrayInputStream(bytes, (int) offset, bytes.length - (int) offset);
        }
    }

    private final InMemoryUserFileRepository repository = new InMemoryUserFileRepository();
    private final MemoryFileStore store = new MemoryFileStore();
    private final FilesModuleImpl module = module(FilesProperties.defaults());

    private FilesModuleImpl module(FilesProperties properties) {
        return new FilesModuleImpl(new UserFileCrudService(repository, Mockito.mock(InterceptorDispatcher.class)),
                store, new ZipInspector(), properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static InputStream zip(Zips zip) {
        return new ByteArrayInputStream(zip.bytes());
    }

    @Test
    void aCodeIsCheckedStoredAndDescribed() {
        Zips code = Zips.with("src/train.py", "print('epoch')").file("src/model.py", "class M: pass");

        FileInfo info = module.upload(ALICE, FileKind.CODE, "mnist", zip(code));

        assertThat(info.owner()).isEqualTo(ALICE);
        assertThat(info.entries()).containsExactly("src/train.py", "src/model.py");
        assertThat(info.sha256()).isEqualTo(Sha256.of(code.bytes()));
        assertThat(info.uploadedAt()).isEqualTo(NOW);
        assertThat(store.files).containsKey(new FileKey(info.sha256()));
    }

    @Test
    void theSameDatasetUploadedByTwoPeopleIsStoredOnce() {
        Zips dataset = Zips.with("labels.csv", "0,1,2");

        FileInfo alice = module.upload(ALICE, FileKind.DATASET, "mnist", zip(dataset));
        FileInfo bob = module.upload(BOB, FileKind.DATASET, "digits", zip(dataset));

        assertThat(alice.id()).isNotEqualTo(bob.id());
        assertThat(alice.sha256()).isEqualTo(bob.sha256());
        assertThat(store.files).hasSize(1);
    }

    @Test
    void aNameIsUniquePerOwnerAndKind() {
        module.upload(ALICE, FileKind.DATASET, "mnist", zip(Zips.with("a.csv", "1")));

        assertThatThrownBy(() -> module.upload(ALICE, FileKind.DATASET, "mnist", zip(Zips.with("b.csv", "2"))))
                .isInstanceOf(ConflictException.class);
        module.upload(ALICE, FileKind.CODE, "mnist", zip(Zips.with("train.py", "x")));
        module.upload(BOB, FileKind.DATASET, "mnist", zip(Zips.with("b.csv", "2")));
    }

    @Test
    void aRefusedZipIsNeitherStoredNorRecorded() {
        assertThatThrownBy(() -> module.upload(ALICE, FileKind.CODE, "evil", zip(Zips.with("../x.py", "x"))))
                .isInstanceOf(ValidationException.class);

        assertThat(store.files).isEmpty();
        assertThat(repository.rows).isEmpty();
    }

    @Test
    void anUploadLargerThanTheLimitIsRefused() {
        FilesProperties small = new FilesProperties(DataSize.ofBytes(100), DataSize.ofMegabytes(1), 10,
                DataSize.ofBytes(100), DataSize.ofMegabytes(1), 10, 200);
        Zips big = Zips.with("train.py", "x".repeat(10_000));

        assertThatThrownBy(() -> module(small).upload(ALICE, FileKind.CODE, "big", zip(big)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("too large");
        assertThat(store.files).isEmpty();
    }

    @Test
    void anUnsafeNameIsRefusedBeforeReceivingTheFile() {
        assertThatThrownBy(() -> module.upload(ALICE, FileKind.DATASET, "../etc", zip(Zips.with("a", "1"))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void eachUserSeesAndDeletesOnlyTheirOwnFiles() {
        FileInfo mine = module.upload(ALICE, FileKind.DATASET, "mnist", zip(Zips.with("a.csv", "1")));
        module.upload(ALICE, FileKind.CODE, "train", zip(Zips.with("train.py", "x")));
        module.upload(BOB, FileKind.DATASET, "secret", zip(Zips.with("s.csv", "2")));

        assertThat(module.listOf("u-1", null)).extracting(FileInfo::name).containsExactly("train", "mnist");
        assertThat(module.listOf("u-1", FileKind.DATASET)).extracting(FileInfo::name).containsExactly("mnist");
        assertThat(module.find("u-2", mine.id())).as("Bob cannot see Alice's file").isEmpty();
        assertThatThrownBy(() -> module.delete("u-2", mine.id())).isInstanceOf(NotFoundException.class);

        module.delete("u-1", mine.id());

        assertThat(module.find("u-1", mine.id())).isEmpty();
    }
}
