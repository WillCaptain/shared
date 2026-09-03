package org.twelve.aipp.collaboration;

import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceGrantAssertionsTest {
    @Test
    void assertionIsSignedActorBoundOperationBoundAndExpiring() throws Exception {
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Instant now = Instant.parse("2026-09-03T08:00:00Z");
        var claims = new ResourceGrantAssertion(
                ResourceGrantAssertion.SCHEMA, "chat-one", "grant", "conversation",
                "owner", "recipient", "note-one", "document", "7", "sha256:abc",
                "view", List.of("view", "propose"), now, now.plusSeconds(120), "nonce");

        String assertion = ResourceGrantAssertions.sign(claims, keys.getPrivate());
        ResourceGrantAssertion verified = ResourceGrantAssertions.verify(assertion, keys.getPublic());

        assertThat(verified.validFor("recipient", "view", now.plusSeconds(30))).isTrue();
        assertThat(verified.validFor("another", "view", now.plusSeconds(30))).isFalse();
        assertThat(verified.validFor("recipient", "propose", now.plusSeconds(30))).isFalse();
        assertThat(verified.validFor("recipient", "view", now.plusSeconds(121))).isFalse();
        assertThatThrownBy(() -> ResourceGrantAssertions.verify(assertion + "x", keys.getPublic()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
