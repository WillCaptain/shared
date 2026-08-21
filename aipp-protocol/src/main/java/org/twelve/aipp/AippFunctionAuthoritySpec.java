package org.twelve.aipp;

import com.fasterxml.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Function-authority protocol: gated tools/skills register through user-one.
 *
 * <p>See {@code spec/function-authority.md}.
 */
public class AippFunctionAuthoritySpec {

    public static final String REGISTER_FUNCTION_TOOL = "register_function";
    public static final String ASSIGN_FUNCTION_TOOL = "assign_function";
    public static final String CHECK_FUNCTION_TOOL = "check_function";
    public static final String OWNER_APP_ID = "user-one";

    public void assertValidFunctionId(String functionId) {
        assertThat(functionId).as("[AIPP Function] function_id required").isNotBlank();
        int idx = functionId.indexOf("::");
        assertThat(idx)
                .as("[AIPP Function] function_id must be {app_id}::{name}: %s", functionId)
                .isGreaterThan(0);
        assertThat(idx)
                .as("[AIPP Function] function_id must be {app_id}::{name}: %s", functionId)
                .isLessThan(functionId.length() - 2);
        assertThat(functionId.substring(0, idx)).isNotBlank();
        assertThat(functionId.substring(idx + 2)).isNotBlank();
    }

    public void assertValidRegisterFunctionResponse(JsonNode response) {
        assertThat(response.path("ok").asBoolean(false))
                .as("[AIPP Function] register_function expects ok=true").isTrue();
        JsonNode fn = response.path("function");
        assertThat(fn.isObject()).as("[AIPP Function] missing function object").isTrue();
        assertValidFunctionId(fn.path("function_id").asText(""));
        assertThat(fn.path("app_id").asText("")).isNotBlank();
        assertThat(fn.path("name").asText("")).isNotBlank();
        String kind = fn.path("kind").asText("tool");
        assertThat(kind).isIn("tool", "skill");
    }

    public void assertUserOneOwnsRegisterFunction(JsonNode toolsResponse) {
        assertThat(toolsResponse.path("app").asText("")).isEqualTo(OWNER_APP_ID);
        JsonNode tools = toolsResponse.path("tools");
        assertThat(tools.isArray()).isTrue();
        boolean advertised = false;
        for (JsonNode tool : tools) {
            if (REGISTER_FUNCTION_TOOL.equals(tool.path("name").asText())) {
                advertised = true;
                break;
            }
        }
        assertThat(advertised)
                .as("[AIPP Function] user-one must advertise register_function")
                .isTrue();
    }

    public void assertRequiresAuthorityIsBooleanWhenPresent(JsonNode tool) {
        if (!tool.has("requires_authority")) return;
        assertThat(tool.get("requires_authority").isBoolean())
                .as("[AIPP Function] requires_authority must be boolean on %s",
                        tool.path("name").asText())
                .isTrue();
    }
}
