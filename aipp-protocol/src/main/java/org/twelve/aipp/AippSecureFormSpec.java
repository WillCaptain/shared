package org.twelve.aipp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract assertions for the Host-owned {@code sys.secure-form} widget.
 *
 * <p>The form schema may cross the Host boundary. Field values must not: they are
 * delivered through a direct, one-time client bridge and only a sanitized status
 * may enter tool results or conversation history.</p>
 */
public final class AippSecureFormSpec {

    public static final String SCHEMA = "aipp.secure-form/v1";
    private static final Set<String> FIELD_TYPES = Set.of(
            "text", "password", "tel", "otp", "email", "url", "boolean", "select");
    private static final Set<String> ACTION_ROLES = Set.of("submit", "cancel");

    public void assertValidSchema(JsonNode data) {
        assertThat(data).as("secure form data").isNotNull();
        assertThat(data.path("schema").asText()).isEqualTo(SCHEMA);
        assertThat(data.path("form_id").asText()).as("one-time form_id").isNotBlank();
        assertThat(data.path("views").isArray()).as("views must be an array").isTrue();
        assertThat(data.path("views").size()).as("at least one view").isGreaterThan(0);
        assertThat(data.path("actions").isArray()).as("actions must be an array").isTrue();

        for (JsonNode view : data.path("views")) {
            assertThat(view.path("id").asText()).as("view id").isNotBlank();
            assertThat(view.path("fields").isArray()).as("view fields").isTrue();
            for (JsonNode field : view.path("fields")) {
                assertThat(field.path("name").asText()).as("field name").isNotBlank();
                assertThat(field.path("type").asText()).isIn(FIELD_TYPES);
                if (field.path("sensitive").asBoolean(false)) {
                    assertThat(field.has("default_value"))
                            .as("sensitive fields must never cross the Host boundary with values")
                            .isFalse();
                }
            }
        }
        for (JsonNode action : data.path("actions")) {
            assertThat(action.path("id").asText()).as("action id").isNotBlank();
            assertThat(action.path("role").asText()).isIn(ACTION_ROLES);
        }
    }

    public AippSecureFormSpec() {}
}
