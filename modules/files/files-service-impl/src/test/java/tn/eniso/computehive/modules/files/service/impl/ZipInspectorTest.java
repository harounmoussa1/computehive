package tn.eniso.computehive.modules.files.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;
import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.modules.files.infra.FileKind;
import tn.eniso.computehive.modules.files.infra.ZipSummary;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipInspectorTest {

    private static final FilesProperties.Limits CODE = FilesProperties.defaults().of(FileKind.CODE);
    private static final FilesProperties.Limits DATASET = FilesProperties.defaults().of(FileKind.DATASET);

    private final ZipInspector inspector = new ZipInspector();

    @TempDir
    Path dir;

    private ZipSummary inspect(Zips zip, FileKind kind, FilesProperties.Limits limits) {
        return inspector.inspect(zip.writeTo(dir), kind, limits);
    }

    private void assertRefused(Zips zip, FileKind kind, String reason) {
        assertThatThrownBy(() -> inspect(zip, kind, kind == FileKind.CODE ? CODE : DATASET))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(reason);
    }

    @Test
    void aCodeZipListsItsFiles() {
        ZipSummary summary = inspect(Zips.with("src/train.py", "print('hi')").file("README.md", "doc"),
                FileKind.CODE, CODE);

        assertThat(summary.entryCount()).isEqualTo(2);
        assertThat(summary.uncompressedBytes()).isEqualTo(14);
        assertThat(summary.entries()).containsExactly("src/train.py", "README.md");
    }

    @Test
    void aDatasetIsCountedButItsFilesAreNotListed() {
        ZipSummary summary = inspect(Zips.with("images/0.png", "pixels").file("labels.csv", "0,1"),
                FileKind.DATASET, DATASET);

        assertThat(summary.entryCount()).isEqualTo(2);
        assertThat(summary.entries()).isEmpty();
    }

    @Test
    void pathsThatEscapeTheJobFolderAreRefused() {
        assertRefused(Zips.with("../evil.py", "x"), FileKind.CODE, "unsafe path");
        assertRefused(Zips.with("src/../../evil.py", "x"), FileKind.CODE, "unsafe path");
        assertRefused(Zips.with("/etc/cron.d/evil", "x"), FileKind.DATASET, "unsafe path");
        assertRefused(Zips.with("C:/Windows/evil.py", "x"), FileKind.DATASET, "unsafe path");
        assertRefused(Zips.with("src/..\\..\\evil.py", "x"), FileKind.CODE, "unsafe path");
        assertRefused(Zips.with("evil\n.py", "x"), FileKind.CODE, "unsafe path");
    }

    @Test
    void aZipMadeOnWindowsWithBackslashesIsReadWithSlashes() {
        // Compress-Archive (Windows PowerShell) écrit des \ : la lecture les traite comme des séparateurs.
        ZipSummary summary = inspect(Zips.with("src\\train.py", "x"), FileKind.CODE, CODE);

        assertThat(summary.entries()).containsExactly("src/train.py");
    }

    @Test
    void symbolicLinksAreRefused() {
        assertRefused(Zips.with("train.py", "x").link("data", "/home"), FileKind.CODE, "symbolic links");
    }

    @Test
    void aZipBombIsRefused() {
        byte[] zeros = new byte[5 * 1024 * 1024];

        assertRefused(new Zips().file("zeros.bin", zeros), FileKind.DATASET, "compression ratio");
    }

    @Test
    void limitsOnTheNumberAndSizeOfFilesApply() {
        FilesProperties.Limits tiny = new FilesProperties.Limits(DataSize.ofMegabytes(1), DataSize.ofBytes(10), 2, 200);

        assertThatThrownBy(() -> inspect(Zips.with("a", "1").file("b", "2").file("c", "3"), FileKind.DATASET, tiny))
                .hasMessageContaining("too many files");
        assertThatThrownBy(() -> inspect(Zips.with("a", "0123456789ABC"), FileKind.DATASET, tiny))
                .hasMessageContaining("too large once unzipped");
    }

    @Test
    void aCodeWithoutPythonOrAnEmptyZipIsRefused() {
        assertRefused(Zips.with("notes.txt", "x"), FileKind.CODE, "at least one .py");
        assertRefused(new Zips(), FileKind.DATASET, "no file");
    }

    @Test
    void somethingThatIsNotAZipIsRefused() throws Exception {
        Path notZip = Files.writeString(dir.resolve("fake.zip"), "this is not a zip");

        assertThatThrownBy(() -> inspector.inspect(notZip, FileKind.DATASET, DATASET))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not a valid zip");
    }
}
