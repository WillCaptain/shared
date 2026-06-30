package org.twelve.aipp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AippUserIdentitySpecTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final AippUserIdentitySpec spec = new AippUserIdentitySpec();

    @Test
    void acceptsProtocolDefaultStub() throws Exception {
        var node = JSON.readTree("""
                {"ok":true,"user":{"id":"001","name":"will"}}
                """);
        assertThatCode(() -> spec.assertProtocolDefaultStub(node)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingUser() throws Exception {
        var node = JSON.readTree("{\"ok\":true}");
        assertThatThrownBy(() -> spec.assertValidGetUserResponse(node)).isNotNull();
    }

    @Test
    void acceptsGetWorkspaceWithNullPath() throws Exception {
        var node = JSON.readTree("""
                {"ok":true,"workspace":null,"default_suffix":"/once"}
                """);
        assertThatCode(() -> spec.assertValidGetWorkspaceResponse(node)).doesNotThrowAnyException();
    }

    @Test
    void acceptsGetWorkspaceWithAbsolutePath() throws Exception {
        var node = JSON.readTree("""
                {"ok":true,"workspace":"/Users/me/Documents/once","default_suffix":"/once"}
                """);
        assertThatCode(() -> spec.assertValidGetWorkspaceResponse(node)).doesNotThrowAnyException();
    }
}
