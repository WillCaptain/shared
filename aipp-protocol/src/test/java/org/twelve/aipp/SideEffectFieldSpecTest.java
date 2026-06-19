package org.twelve.aipp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * `side_effect` retry-safety axis (spec/tool-manifest.md §3.1).
 *
 * <p>This is the ONLY signal the Host orchestrator uses to decide whether a
 * failed plan step may be auto-retried. It is orthogonal to placement,
 * `mutates_display`, and `requires_confirmation`. The validator only checks
 * the enum value; the fail-closed default (unspecified ⇒ treat as `mutating`)
 * is Host behavior, not a manifest validity rule.
 */
class SideEffectFieldSpecTest {

    private final AippAppSpec spec = new AippAppSpec();
    private final ObjectMapper json = new ObjectMapper();

    private JsonNode tool(String body) throws Exception {
        return json.readTree(body);
    }

    @Test
    void noneValuePasses() throws Exception {
        assertThatCode(() -> spec.assertValidSideEffectField(tool("""
                {"name":"recipe_get","side_effect":"none"}
                """))).doesNotThrowAnyException();
    }

    @Test
    void idempotentValuePasses() throws Exception {
        assertThatCode(() -> spec.assertValidSideEffectField(tool("""
                {"name":"profile_upsert","side_effect":"idempotent"}
                """))).doesNotThrowAnyException();
    }

    @Test
    void mutatingValuePasses() throws Exception {
        assertThatCode(() -> spec.assertValidSideEffectField(tool("""
                {"name":"send_email","side_effect":"mutating"}
                """))).doesNotThrowAnyException();
    }

    @Test
    void absentFieldPasses() throws Exception {
        // Unspecified is legal at the manifest layer; Host fails closed (treats as mutating).
        assertThatCode(() -> spec.assertValidSideEffectField(tool("""
                {"name":"legacy_tool"}
                """))).doesNotThrowAnyException();
    }

    @Test
    void unknownValueFails() throws Exception {
        assertThatThrownBy(() -> spec.assertValidSideEffectField(tool("""
                {"name":"bad_tool","side_effect":"readonly"}
                """))).hasMessageContaining("side_effect");
    }

    @Test
    void nonStringValueFails() throws Exception {
        assertThatThrownBy(() -> spec.assertValidSideEffectField(tool("""
                {"name":"bad_tool","side_effect":true}
                """))).hasMessageContaining("side_effect");
    }

    @Test
    void wiredIntoToolsApiStructure() throws Exception {
        // The per-tool gate must run from assertValidToolsApiStructure, like client-exec fields.
        assertThatThrownBy(() -> spec.assertValidToolsApiStructure(tool("""
                {"app":"recipe-one","version":"1.0","tools":[
                  {"name":"recipe_get","description":"x","parameters":{"type":"object","properties":{},"required":[]},
                   "canvas":{"triggers":false},"side_effect":"sometimes"}
                ]}
                """))).hasMessageContaining("side_effect");
    }
}
