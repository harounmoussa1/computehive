package tn.eniso.computehive.extensions.files.api;

import org.junit.jupiter.api.Test;
import tn.eniso.computehive.core.infra.exception.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileKeyTest {

    private static final String SHA = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    void aKeyIsTheContentDigestStoredUnderFiles() {
        FileKey key = new FileKey(SHA);

        assertThat(key.path()).isEqualTo("files/" + SHA);
        assertThat(FileKey.parse(SHA.toUpperCase())).isEqualTo(key);
    }

    @Test
    void anythingElseThanADigestIsRejected() {
        assertThatThrownBy(() -> FileKey.parse("../etc/passwd")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> FileKey.parse(SHA + "0")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> FileKey.parse(null)).isInstanceOf(ValidationException.class);
    }
}
