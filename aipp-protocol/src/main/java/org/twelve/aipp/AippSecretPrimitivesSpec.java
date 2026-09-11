package org.twelve.aipp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract assertions for the generic {@code secret} client capability
 * (spec: client-execution.md §4.3).
 *
 * <p>The flow belongs to the AIPP that owns it; the client executor only collects,
 * stores, and types values. Collected values never cross the client boundary, so
 * results carry opaque handles instead.</p>
 */
public final class AippSecretPrimitivesSpec {

    public static final String CAPABILITY = "secret";
    public static final List<String> TOOLS = List.of(
            "secure_input", "secret_list", "secret_put", "secret_type", "secret_delete");
    private static final Set<String> VALUE_BEARING_KEYS = Set.of(
            "values", "value", "password", "secret", "code", "otp", "totp", "token", "pin");

    /** Every declared secret tool must execute on the client under this capability. */
    public void assertToolDeclarations(JsonNode tools) {
        assertThat(tools).as("tools").isNotNull();
        for (String name : TOOLS) {
            JsonNode tool = null;
            for (JsonNode candidate : tools) {
                if (name.equals(candidate.path("name").asText())) {
                    tool = candidate;
                    break;
                }
            }
            assertThat(tool).as("declared tool %s", name).isNotNull();
            assertThat(tool.path("execution_surface").asText()).isEqualTo("client");
            assertThat(tool.path("client_capability").asText()).isEqualTo(CAPABILITY);
        }
    }

    /** A secure-input result may describe what was collected, never what it was. */
    public void assertValueFreeResult(JsonNode result) {
        assertThat(result).as("secure_input result").isNotNull();
        assertNoValueBearingKeys(result);
        String status = result.path("status").asText();
        if ("submitted".equals(status)) {
            assertThat(result.path("values_ref").asText()).as("opaque handle").startsWith("vref_");
            assertThat(result.path("fields_present").isArray()).as("field names only").isTrue();
            for (JsonNode field : result.path("fields_present")) {
                assertThat(field.isTextual()).as("fields_present holds names").isTrue();
            }
            for (JsonNode flag : result.path("flags")) {
                assertThat(flag.isBoolean()).as("flags hold only boolean choices").isTrue();
            }
        }
    }

    private void assertNoValueBearingKeys(JsonNode node) {
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(name -> assertThat(VALUE_BEARING_KEYS)
                    .as("result key %s must not carry a collected value", name)
                    .doesNotContain(name));
        }
        for (JsonNode child : node) {
            assertNoValueBearingKeys(child);
        }
    }

    public AippSecretPrimitivesSpec() {}
}
