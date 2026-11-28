package tn.eniso.computehive.core.infra.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256Test {

    @Test
    void theDigestIsComputedWhileReading() throws Exception {
        byte[] content = "checkpoint content".getBytes(StandardCharsets.UTF_8);
        try (Sha256.DigestingStream stream = new Sha256.DigestingStream(new ByteArrayInputStream(content))) {
            stream.readAllBytes();

            assertThat(stream.size()).isEqualTo(content.length);
            assertThat(stream.sha256()).isEqualTo(Sha256.of(content)).matches("[0-9a-f]{64}");
        }
    }

    @Test
    void theDigestOfAKnownValueIsTheStandardOne() {
        assertThat(Sha256.of("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
