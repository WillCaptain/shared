package org.twelve.aipp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AippUserIdentitySpecTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final AippUserIdentitySpec spec = new AippUserIdentitySpec();

    @Test
    void acceptsUserOneProfileResponse() throws Exception {
        var node = JSON.readTree("""
                {"ok":true,"user":{"id":"9f706fa4-2ca7-4f06-a0d3-a33e5c449d42","name":"Will"}}
                """);
        assertThatCode(() -> spec.assertValidGetUserResponse(node)).doesNotThrowAnyException();
    }

    @Test
    void requiresUserOneToOwnGetUserCatalog() throws Exception {
        var node = JSON.readTree("""
                {"app":"user-one","tools":[{"name":"get_user"}]}
                """);
        assertThatCode(() -> spec.assertUserOneOwnsGetUser(node)).doesNotThrowAnyException();
    }

    @Test
    void rejectsGetUserAdvertisedByNoteOne() throws Exception {
        var node = JSON.readTree("""
                {"app":"note-one","tools":[{"name":"get_user"}]}
                """);
        assertThatThrownBy(() -> spec.assertUserOneOwnsGetUser(node))
                .hasMessageContaining("get_user owner");
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
