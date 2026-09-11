package org.twelve.aipp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AippSecretPrimitivesSpecTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AippSecretPrimitivesSpec spec = new AippSecretPrimitivesSpec();

    @Test
    void acceptsClientDeclarationsForEveryPrimitive() throws Exception {
        StringBuilder tools = new StringBuilder("[");
        for (String name : AippSecretPrimitivesSpec.TOOLS) {
            tools.append("""
                    {"name":"%s","execution_surface":"client","client_capability":"secret"},
                    """.formatted(name));
        }
        tools.append("""
                {"name":"browser_click","execution_surface":"client","client_capability":"browser"}]
                """);
        JsonNode declared = mapper.readTree(tools.toString());
        assertThatNoException().isThrownBy(() -> spec.assertToolDeclarations(declared));
    }

    @Test
    void rejectsSecretToolThatWouldRunOnTheServer() throws Exception {
        JsonNode declared = mapper.readTree("""
                [{"name":"secure_input","execution_surface":"server","client_capability":"secret"}]
                """);
        assertThatThrownBy(() -> spec.assertToolDeclarations(declared))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void acceptsHandleOnlyResultAndRejectsCollectedValues() throws Exception {
        JsonNode handleOnly = mapper.readTree("""
                {
                  "ok": true,
                  "status": "submitted",
                  "action": "submit",
                  "fields_present": ["account", "secret", "keep"],
                  "flags": {"keep": true},
                  "values_ref": "vref_opaque",
                  "expires_at": 1
                }
                """);
        assertThatNoException().isThrownBy(() -> spec.assertValueFreeResult(handleOnly));

        JsonNode leaked = mapper.readTree("""
                {"status":"submitted","values_ref":"vref_opaque","values":{"secret":"hunter2"}}
                """);
        assertThatThrownBy(() -> spec.assertValueFreeResult(leaked))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("must not carry a collected value");
    }
}
