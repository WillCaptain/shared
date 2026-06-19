package org.twelve.aipp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Client-execution manifest invariants (spec/client-execution.md §2):
 * the `client` surface label is the ONLY thing separating "LLM's local
 * (server)" from "user's machine (client)" — so it must be well-formed.
 */
class ClientExecutionFieldsSpecTest {

    private final AippAppSpec spec = new AippAppSpec();
    private final ObjectMapper json = new ObjectMapper();

    private JsonNode tool(String body) throws Exception {
        return json.readTree(body);
    }

    @Test
    void clientToolWithCapabilityPasses() throws Exception {
        assertThatCode(() -> spec.assertValidClientExecutionFields(tool("""
                {"name":"terminal_run","execution_surface":"client",
                 "client_capability":"terminal","requires_confirmation":false}
                """))).doesNotThrowAnyException();
    }

    @Test
    void serverToolWithoutClientFieldsPasses() throws Exception {
        assertThatCode(() -> spec.assertValidClientExecutionFields(tool("""
                {"name":"memory_view"}
                """))).doesNotThrowAnyException();
    }

    @Test
    void clientToolMissingCapabilityFails() throws Exception {
        assertThatThrownBy(() -> spec.assertValidClientExecutionFields(tool("""
                {"name":"bad_tool","execution_surface":"client"}
                """))).hasMessageContaining("client_capability");
    }

    @Test
    void unknownSurfaceFails() throws Exception {
        assertThatThrownBy(() -> spec.assertValidClientExecutionFields(tool("""
                {"name":"bad_tool","execution_surface":"local"}
                """))).hasMessageContaining("execution_surface");
    }

    @Test
    void capabilityOnServerToolFails() throws Exception {
        assertThatThrownBy(() -> spec.assertValidClientExecutionFields(tool("""
                {"name":"bad_tool","client_capability":"terminal"}
                """))).hasMessageContaining("client_capability");
    }

    @Test
    void nonBooleanConfirmationFails() throws Exception {
        assertThatThrownBy(() -> spec.assertValidClientExecutionFields(tool("""
                {"name":"bad_tool","execution_surface":"client",
                 "client_capability":"terminal","requires_confirmation":"yes"}
                """))).hasMessageContaining("requires_confirmation");
    }

    @Test
    void dualSurfaceWithCapabilityAndPackagePasses() throws Exception {
        assertThatCode(() -> spec.assertValidClientExecutionFields(tool("""
                {"name":"parse_file","execution_surface":["server","client"],
                 "client_capability":"std.file.parse.v1",
                 "client_package":{"runtime":"jar","artifact":"/api/client-package/parse_file-1.0.jar"}}
                """))).doesNotThrowAnyException();
    }

    @Test
    void dualSurfaceMissingCapabilityFails() throws Exception {
        assertThatThrownBy(() -> spec.assertValidClientExecutionFields(tool("""
                {"name":"parse_file","execution_surface":["server","client"]}
                """))).hasMessageContaining("client_capability");
    }

    @Test
    void clientPackageOnServerOnlyToolFails() throws Exception {
        assertThatThrownBy(() -> spec.assertValidClientExecutionFields(tool("""
                {"name":"bad_tool",
                 "client_package":{"runtime":"jar","artifact":"x.jar"}}
                """))).hasMessageContaining("client_package");
    }

    @Test
    void unknownPackageRuntimeFails() throws Exception {
        assertThatThrownBy(() -> spec.assertValidClientExecutionFields(tool("""
                {"name":"parse_file","execution_surface":["server","client"],
                 "client_capability":"std.file.parse.v1",
                 "client_package":{"runtime":"wasm","artifact":"x"}}
                """))).hasMessageContaining("runtime");
    }
}
