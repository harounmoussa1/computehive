package tn.eniso.computehive.extensions.checkpoints.api;

import org.junit.jupiter.api.Test;
import tn.eniso.computehive.core.infra.exception.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckpointKeyTest {

    @Test
    void aKeyIsWrittenAndReadBack() {
        CheckpointKey key = new CheckpointKey("d18a11f3-9960-4188-b996-b389ef03d216", 2, 4);

        assertThat(key.path()).isEqualTo("d18a11f3-9960-4188-b996-b389ef03d216/attempts/2/epochs/4");
        assertThat(CheckpointKey.parse(key.path())).isEqualTo(key);
    }

    @Test
    void aKeyCannotEscapeTheStorage() {
        assertThatThrownBy(() -> new CheckpointKey("../etc", 1, 1)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> CheckpointKey.parse("../../passwd/attempts/1/epochs/1"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> CheckpointKey.parse("j-7/attempts/1/epochs/1/../../x"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void invalidKeysAreRejected() {
        assertThatThrownBy(() -> CheckpointKey.parse("j-7/epochs/4")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> CheckpointKey.parse(null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new CheckpointKey("j-7", 0, 1)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new CheckpointKey("j-7", 1, 0)).isInstanceOf(ValidationException.class);
    }
}
