package tn.eniso.computehive.modules.files.infra;

import org.junit.jupiter.api.Test;
import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.core.infra.ref.UserRef;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserFileTest {

    private static final UserRef ALICE = new UserRef("u-1", "Alice", "MEMBER");
    private static final Instant NOW = Instant.parse("2026-11-28T10:00:00Z");

    @Test
    void anUploadedFileKeepsWhatTheInspectionFound() {
        UserFile file = UserFile.uploaded("f-1", ALICE, FileKind.CODE, "mnist-v2", "a".repeat(64), 1_234,
                new ZipSummary(2, 5_000, List.of("train.py", "model.py")), NOW);

        FileInfo info = file.toInfo();

        assertThat(info.name()).isEqualTo("mnist-v2");
        assertThat(info.entries()).containsExactly("train.py", "model.py");
        assertThat(info.uncompressedBytes()).isEqualTo(5_000);
        assertThat(file.isOwnedBy("u-1")).isTrue();
        assertThat(file.isOwnedBy("u-2")).isFalse();
    }

    @Test
    void aNameMustBeASafeFolderName() {
        assertThat(UserFile.checkName("cifar_10.v2")).isEqualTo("cifar_10.v2");
        for (String unsafe : new String[]{"..", ".hidden", "a/b", "a\\b", "", "with space", "x".repeat(65)}) {
            assertThatThrownBy(() -> UserFile.checkName(unsafe)).as(unsafe).isInstanceOf(ValidationException.class);
        }
        assertThatThrownBy(() -> UserFile.checkName(null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void aCopyIsIndependent() {
        UserFile file = UserFile.uploaded("f-1", ALICE, FileKind.DATASET, "mnist", "a".repeat(64), 10,
                new ZipSummary(1, 10, List.of()), NOW);
        UserFile copy = file.copy();

        file.setName("other");

        assertThat(copy.getName()).isEqualTo("mnist");
    }
}
