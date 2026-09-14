package org.twelve.aipp.identity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.twelve.aipp.AippAppSpec;
import static org.assertj.core.api.Assertions.*;

class InvocationIdentityManifestTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AippAppSpec spec = new AippAppSpec();
    @Test void acceptsAbsentOrExactSupportedRequirement() throws Exception {
        spec.assertValidInvocationIdentity(json.readTree("{}"));
        spec.assertValidInvocationIdentity(json.readTree("{\"invocation_identity\":\"verified-request-v1\"}"));
    }
    @Test void rejectsNullBooleanObjectBlankAndUnknownVersions() throws Exception {
        for (String value : new String[]{"null", "true", "{}", "[]", "1", "\"\"", "\"verified-request-v2\"", "\" verified-request-v1\""}) {
            var tool = json.readTree("{\"invocation_identity\":" + value + "}");
            assertThatThrownBy(() -> spec.assertValidInvocationIdentity(tool)).isInstanceOf(AssertionError.class);
        }
    }
    @Test void normalToolValidationEnforcesTheRequirement() throws Exception {
        var tool = json.readTree("{\"name\":\"sample_read\",\"description\":\"Read sample data\",\"parameters\":{\"type\":\"object\",\"properties\":{}},\"canvas\":{\"triggers\":false},\"invocation_identity\":\"verified-request-v1\"}");
        ((com.fasterxml.jackson.databind.node.ObjectNode) tool.get("parameters")).putArray("required");
        spec.assertValidSkillStructure(tool);
        ((com.fasterxml.jackson.databind.node.ObjectNode) tool).put("invocation_identity", "unknown");
        assertThatThrownBy(() -> spec.assertValidSkillStructure(tool)).isInstanceOf(AssertionError.class);
    }
}
