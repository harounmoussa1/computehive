package tn.eniso.computehive.core.infra.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTokenTest {

    private final AgentToken token = AgentToken.of("labo-s3cr3t");

    @Test
    void theRightTokenIsAccepted() {
        assertThat(token.enabled()).isTrue();
        assertThat(token.accepts("Bearer labo-s3cr3t")).isTrue();
        assertThat(token.accepts("bearer labo-s3cr3t")).as("the scheme is case-insensitive").isTrue();
        assertThat(token.accepts(AgentToken.authorizationFor("labo-s3cr3t"))).isTrue();
    }

    @Test
    void aMissingWrongOrMalformedTokenIsRejected() {
        assertThat(token.accepts(null)).isFalse();
        assertThat(token.accepts("")).isFalse();
        assertThat(token.accepts("Bearer wrong")).isFalse();
        assertThat(token.accepts("Bearer labo-s3cr3t-and-more")).isFalse();
        assertThat(token.accepts("labo-s3cr3t")).as("without the Bearer scheme").isFalse();
        assertThat(token.accepts("Basic labo-s3cr3t")).isFalse();
    }

    @Test
    void withoutConfiguredTokenEverythingIsAccepted() {
        assertThat(AgentToken.of(null).enabled()).isFalse();
        assertThat(AgentToken.of("  ").accepts(null)).isTrue();
        assertThat(AgentToken.disabled().accepts("Bearer anything")).isTrue();
    }

    @Test
    void theTokenNeverAppearsInLogs() {
        assertThat(token.toString()).doesNotContain("labo-s3cr3t");
    }
}
