package org.twelve.aipp.tools;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.twelve.aipp.AippAppSpec;
import static org.assertj.core.api.Assertions.*;

class AgentResultPolicyTest {
    private final Map<String, Object> tool = Map.of("agent_result", Map.of(
            "evidence_paths", List.of("/records/0"), "synthesize", true,
            "projection", Map.of("rows", Map.of("path", "/records", "limit", 1,
                    "items", Map.of("label", "/name")))));
    @Test void projectsBoundedEvidenceUsingProviderDataOnly() throws Exception {
        var result = AgentResultPolicy.apply(tool, "{\"records\":[{\"name\":\"one\",\"large\":123},{\"name\":\"two\"}]}");
        assertThat(result.evidenceReady()).isTrue();
        assertThat(new ObjectMapper().readTree(result.content()).path("rows").size()).isEqualTo(1);
        assertThat(result.content()).contains("one").doesNotContain("large", "two");
    }
    @Test void neverMasksFailuresPausesOrPresentation() {
        for (String extra : List.of("\"ok\":false", "\"error\":{\"reason\":\"failed\"}",
                "\"status\":\"awaiting_confirmation\"", "\"status\":\"pending\"",
                "\"html_widget\":{}", "\"canvas\":{}", "\"pop_widget\":{}", "\"new_session\":{}")) {
            String raw = "{\"records\":[{\"name\":\"one\"}]," + extra + "}";
            assertThat(AgentResultPolicy.apply(tool, raw)).isEqualTo(new AgentResultPolicy.Result(raw, false));
        }
    }
    @Test void absentEvidenceAndMalformedPolicyLeaveReceiptsUntouched() {
        for (String raw : List.of("not json", "{}", "{\"records\":[]}"))
            assertThat(AgentResultPolicy.apply(tool, raw)).isEqualTo(new AgentResultPolicy.Result(raw, false));
        String raw = "{\"records\":[{\"name\":\"one\"}]}";
        assertThat(AgentResultPolicy.apply(Map.of(), raw)).isEqualTo(new AgentResultPolicy.Result(raw, false));
        var invalid = Map.<String, Object>of("agent_result", Map.of(
                "evidence_paths", List.of("/records/0"), "projection", Map.of("x", "not-a-pointer")));
        assertThat(AgentResultPolicy.apply(invalid, raw)).isEqualTo(new AgentResultPolicy.Result(raw, false));
    }
    @Test void validatesAndStripsHostPolicyMetadata() {
        var json = new ObjectMapper();
        var spec = new AippAppSpec();
        spec.assertValidAgentPolicy(json.valueToTree(tool));
        assertThatThrownBy(() -> spec.assertValidAgentPolicy(json.valueToTree(
                Map.of("router_defer_arguments", "yes")))).isInstanceOf(AssertionError.class);
        assertThat(ToolPlacement.stripPlacementForLlm(Map.of(
                "name", "demo", "router_defer_arguments", true, "agent_result", tool.get("agent_result"))))
                .doesNotContainKeys("router_defer_arguments", "agent_result");
    }
}
