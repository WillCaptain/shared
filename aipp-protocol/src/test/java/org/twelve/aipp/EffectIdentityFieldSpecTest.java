package org.twelve.aipp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EffectIdentityFieldSpecTest {

    private final AippAppSpec spec = new AippAppSpec();
    private final ObjectMapper json = new ObjectMapper();

    private JsonNode tool(String body) throws Exception {
        return json.readTree(body);
    }

    @Test
    void absentFieldPasses() throws Exception {
        assertThatCode(() -> spec.assertValidEffectIdentityField(tool("""
                {"name":"legacy_tool"}
                """))).doesNotThrowAnyException();
    }

    @Test
    void falsePassesOnAnySurface() throws Exception {
        assertThatCode(() -> spec.assertValidEffectIdentityField(tool("""
                {"name":"server_tool","effect_identity":false}
                """))).doesNotThrowAnyException();
    }

    @Test
    void trueRequiresClientSurface() throws Exception {
        assertThatCode(() -> spec.assertValidEffectIdentityField(tool("""
                {"name":"example_write","execution_surface":"client","effect_identity":true}
                """))).doesNotThrowAnyException();
        assertThatThrownBy(() -> spec.assertValidEffectIdentityField(tool("""
                {"name":"server_tool","execution_surface":"server","effect_identity":true}
                """))).hasMessageContaining("effect_identity");
    }

    @Test
    void nonBooleanFails() throws Exception {
        assertThatThrownBy(() -> spec.assertValidEffectIdentityField(tool("""
                {"name":"bad_tool","effect_identity":{"kind":"shell_redirect"}}
                """))).hasMessageContaining("effect_identity");
    }

    @Test
    void wiredIntoToolsApiStructure() throws Exception {
        assertThatThrownBy(() -> spec.assertValidToolsApiStructure(tool("""
                {"app":"example-one","version":"1.0","tools":[
                  {"name":"example_write","description":"x","parameters":{"type":"object","properties":{},"required":[]},
                   "canvas":{"triggers":false},"execution_surface":"client","client_capability":"files",
                   "effect_identity":"yes"}
                ]}
                """))).hasMessageContaining("effect_identity");
    }
}
