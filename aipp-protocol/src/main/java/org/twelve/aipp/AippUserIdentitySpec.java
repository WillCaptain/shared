package org.twelve.aipp;

import com.fasterxml.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User identity protocol — {@code get_user} tool response shape and protocol default stub.
 *
 * <p>See {@code spec/user-identity.md}.
 */
public class AippUserIdentitySpec {

    /** Protocol default stub user id when no user management AIPP is registered. */
    public static final String DEFAULT_USER_ID = "001";

    /** Protocol default stub display name. */
    public static final String DEFAULT_USER_NAME = "will";

    /** Logical workspace suffix when no per-machine path is bound yet (note-one). */
    public static final String DEFAULT_WORKSPACE_SUFFIX = "/once";

    /** Validates a successful {@code get_user} tool response. */
    public void assertValidGetUserResponse(JsonNode response) {
        assertThat(response).as("[AIPP User] response must not be null").isNotNull();
        assertThat(response.path("ok").asBoolean(false))
                .as("[AIPP User] get_user expects ok=true").isTrue();
        JsonNode user = response.get("user");
        assertThat(user).as("[AIPP User] get_user missing 'user' object").isNotNull();
        assertThat(user.isObject()).as("[AIPP User] 'user' must be an object").isTrue();
        assertThat(user.path("id").asText("").trim())
                .as("[AIPP User] user.id must be non-blank").isNotBlank();
        assertThat(user.path("name").asText("").trim())
                .as("[AIPP User] user.name must be non-blank").isNotBlank();
    }

    /** Validates the protocol default stub payload. */
    public void assertProtocolDefaultStub(JsonNode response) {
        assertValidGetUserResponse(response);
        assertThat(response.path("user").path("id").asText())
                .as("[AIPP User] default stub user.id")
                .isEqualTo(DEFAULT_USER_ID);
        assertThat(response.path("user").path("name").asText())
                .as("[AIPP User] default stub user.name")
                .isEqualTo(DEFAULT_USER_NAME);
    }

    /** Validates {@code get_workspace} response fields. */
    public void assertValidGetWorkspaceResponse(JsonNode response) {
        assertThat(response).isNotNull();
        assertThat(response.path("ok").asBoolean(false))
                .as("[AIPP User] get_workspace expects ok=true").isTrue();
        assertThat(response.has("default_suffix"))
                .as("[AIPP User] get_workspace missing default_suffix").isTrue();
        assertThat(response.path("default_suffix").asText("").trim())
                .as("[AIPP User] default_suffix must be non-blank").isNotBlank();
        if (response.has("workspace") && !response.get("workspace").isNull()) {
            assertThat(response.path("workspace").asText("").trim())
                    .as("[AIPP User] workspace path must be non-blank when present").isNotBlank();
        }
    }
}
