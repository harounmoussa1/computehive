package tn.eniso.computehive.modules.files.dal.jpa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.modules.files.dal.api.UserFileRepository;
import tn.eniso.computehive.modules.files.infra.FileKind;
import tn.eniso.computehive.modules.files.infra.UserFile;
import tn.eniso.computehive.modules.files.infra.ZipSummary;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste l'implémentation JPA sur un vrai PostgreSQL : migration, correspondance entité ↔ table (validée par
 * Hibernate), requêtes et nom unique par utilisateur et par type. Ignoré si Docker n'est pas disponible.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class JpaUserFileRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Instant T0 = Instant.parse("2026-11-28T10:00:00Z");
    private static final UserRef ALICE = new UserRef("u-1", "Alice", "MEMBER");
    private static final UserRef BOB = new UserRef("u-2", "Bob", "MEMBER");

    @Autowired
    private UserFileRepository repository;

    private static UserFile file(String id, UserRef owner, FileKind kind, String name, List<String> entries) {
        return UserFile.uploaded(id, owner, kind, name, "a".repeat(64), 1_000,
                new ZipSummary(Math.max(1, entries.size()), 4_000, entries), T0);
    }

    @Test
    void aFileIsSavedAndReloadedWithItsOwnerAndEntries() {
        UserFile saved = repository.save(file("f-1", ALICE, FileKind.CODE, "mnist", List.of("train.py", "src/model.py")));

        UserFile loaded = repository.findById("f-1").orElseThrow();

        assertThat(saved.getVersion()).isZero();
        assertThat(loaded).usingRecursiveComparison().isEqualTo(saved);
        assertThat(loaded.getOwner()).isEqualTo(ALICE);
        assertThat(loaded.getEntries()).containsExactly("train.py", "src/model.py");
    }

    @Test
    void aDatasetHasNoEntryList() {
        repository.save(file("f-1", ALICE, FileKind.DATASET, "mnist", List.of()));

        assertThat(repository.findById("f-1").orElseThrow().getEntries()).isEmpty();
    }

    @Test
    void aLibraryListsOnlyItsOwnerFilesByKindThenName() {
        repository.save(file("f-1", ALICE, FileKind.DATASET, "mnist", List.of()));
        repository.save(file("f-2", ALICE, FileKind.CODE, "train", List.of("train.py")));
        repository.save(file("f-3", ALICE, FileKind.DATASET, "cifar", List.of()));
        repository.save(file("f-4", BOB, FileKind.DATASET, "mine", List.of()));

        assertThat(repository.findByOwner("u-1", null)).extracting(UserFile::getName)
                .containsExactly("train", "cifar", "mnist");
        assertThat(repository.findByOwner("u-1", FileKind.DATASET)).extracting(UserFile::getName)
                .containsExactly("cifar", "mnist");
        assertThat(repository.findByOwnerKindAndName("u-2", FileKind.DATASET, "mine")).isPresent();
    }

    @Test
    void aNameIsUniquePerOwnerAndKind() {
        repository.save(file("f-1", ALICE, FileKind.DATASET, "mnist", List.of()));
        repository.save(file("f-2", ALICE, FileKind.CODE, "mnist", List.of("train.py")));
        repository.save(file("f-3", BOB, FileKind.DATASET, "mnist", List.of()));

        assertThatThrownBy(() -> repository.save(file("f-4", ALICE, FileKind.DATASET, "mnist", List.of())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aFileCanBeDeleted() {
        repository.save(file("f-1", ALICE, FileKind.DATASET, "mnist", List.of()));

        repository.delete("f-1");

        assertThat(repository.findById("f-1")).isEmpty();
    }
}
