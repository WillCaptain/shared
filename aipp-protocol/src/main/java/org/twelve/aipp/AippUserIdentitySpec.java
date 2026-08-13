package org.twelve.aipp;

import com.fasterxml.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User identity protocol owned by user-one.
 *
 * <p>user-one resolves {@code get_user} against an external account authority. The Host invokes
 * user-one to validate the active principal and forwards only that trusted principal to consumer
 * AIPPs. Consumer apps such as note-one must not advertise {@code get_user} or manufacture a
 * production identity stub. See {@code spec/user-identity.md}.
 */
public class AippUserIdentitySpec {

    /** Sole AIPP owner of the user profile capability. */
    public static final String GET_USER_OWNER_APP_ID = "user-one";

    /** User profile tool resolved through the external account authority. */
    public static final String GET_USER_TOOL_NAME = "get_user";

    /**
     * Legacy compatibility sentinel. It is not an authenticated principal and MUST NOT be used as
     * a production fallback.
     */
    @Deprecated
    public static final String DEFAULT_USER_ID = "001";

    /** Legacy compatibility display value; not a user-one profile response. */
    @Deprecated
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

    /** Validates that the identity-provider catalog is user-one and advertises {@code get_user}. */
    public void assertUserOneOwnsGetUser(JsonNode toolsResponse) {
        assertThat(toolsResponse).as("[AIPP User] tools response must not be null").isNotNull();
        assertThat(toolsResponse.path("app").asText(""))
                .as("[AIPP User] get_user owner")
                .isEqualTo(GET_USER_OWNER_APP_ID);
        JsonNode tools = toolsResponse.path("tools");
        assertThat(tools.isArray()).as("[AIPP User] tools must be an array").isTrue();
        boolean advertised = false;
        for (JsonNode tool : tools) {
            if (GET_USER_TOOL_NAME.equals(tool.path("name").asText())) {
                advertised = true;
                break;
            }
        }
        assertThat(advertised).as("[AIPP User] user-one must advertise get_user").isTrue();
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
