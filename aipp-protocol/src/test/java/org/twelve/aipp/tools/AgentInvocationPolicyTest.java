package org.twelve.aipp.tools;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.twelve.aipp.AippAppSpec;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class AgentInvocationPolicyTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AippAppSpec spec = new AippAppSpec();
    @Test void optInAndContextPointerAreGenericAndHiddenFromModelSchema() {
        var tool = Map.<String,Object>of("name", "sample",
                "inject_context", Map.of("selected_resources", true, "user_message", true),
                "pre_turn_context_pointer", "/arbitrary/reference");
        spec.assertValidAgentPolicy(json.valueToTree(tool));
        assertThat(ToolPlacement.stripPlacementForLlm(tool))
                .doesNotContainKeys("inject_context", "pre_turn_context_pointer");
    }
    @Test void invalidDeclarationsAreRejected() {
        assertThatThrownBy(() -> spec.assertValidAgentPolicy(json.valueToTree(
                Map.of("inject_context", Map.of("selected_resources", "yes"))))).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> spec.assertValidAgentPolicy(json.valueToTree(
                Map.of("pre_turn_context_pointer", "not-a-pointer")))).isInstanceOf(AssertionError.class);
    }
    @Test void providerLifecycleExtensionIsGenericAndStrippedFromModelTools() {
        var tool = Map.<String,Object>of("name", "sample", "pre_turn_context_pointer", "/text",
                "pre_turn_state_pointer", "/state", "pre_turn_cache_ttl_ms", 0,
                "inject_context", Map.of("execution_scope", true, "provider_contexts", true));
        spec.assertValidAgentPolicy(json.valueToTree(tool));
        assertThat(ToolPlacement.stripPlacementForLlm(tool)).doesNotContainKeys(
                "pre_turn_state_pointer", "pre_turn_cache_ttl_ms", "inject_context");
    }
    @Test void invalidStatePointerLeaseAndContextOptInsAreRejected() {
        for (var tool : java.util.List.of(
                Map.of("pre_turn_state_pointer", "not-a-pointer"),
                Map.of("pre_turn_state_pointer", "/state"),
                Map.of("pre_turn_cache_ttl_ms", -1), Map.of("pre_turn_cache_ttl_ms", 60001),
                Map.of("pre_turn_cache_ttl_ms", "100"),
                Map.of("inject_context", Map.of("execution_scope", "yes")),
                Map.of("inject_context", Map.of("provider_contexts", "yes"))))
            assertThatThrownBy(() -> spec.assertValidAgentPolicy(json.valueToTree(tool)))
                    .isInstanceOf(AssertionError.class);
    }
}
