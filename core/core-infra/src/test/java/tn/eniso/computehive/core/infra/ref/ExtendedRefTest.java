package tn.eniso.computehive.core.infra.ref;

import org.junit.jupiter.api.Test;
import tn.eniso.computehive.core.infra.exception.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtendedRefTest {

    @Test
    void keepsIdNameAndType() {
        MachineRef ref = new MachineRef("m-1", "labo-pc-12", "GPU");

        assertThat(ref.id()).isEqualTo("m-1");
        assertThat(ref.name()).isEqualTo("labo-pc-12");
        assertThat(ref.type()).isEqualTo("GPU");
    }

    @Test
    void rejectsBlankId() {
        assertThatThrownBy(() -> new JobRef(" ", "mnist", "MNIST_TRAIN"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("JobRef.id");
        assertThatThrownBy(() -> new UserRef(null, "Haroun", "MEMBER"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void refsWithSameValuesAreEqual() {
        assertThat(new UserRef("u-1", "Haroun", "MEMBER"))
                .isEqualTo(new UserRef("u-1", "Haroun", "MEMBER"));
    }
}
