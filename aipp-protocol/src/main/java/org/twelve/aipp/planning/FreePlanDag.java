package org.twelve.aipp.planning;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/** Wire model for {@code worldone.free_plan/v2}. */
public record FreePlanDag(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("plan_id") String planId,
        int revision,
        String goal,
        @JsonProperty("source_message") String sourceMessage,
        String status,
        Scope scope,
        List<Node> nodes,
        List<Edge> edges,
        List<Map<String, Object>> evidence) {

    public static final String SCHEMA_VERSION = "worldone.free_plan/v2";

    public FreePlanDag {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public record Scope(
            @JsonProperty("app_ids") List<String> appIds,
            @JsonProperty("capability_ids") List<String> capabilityIds,
            @JsonProperty("allows_new_read_only") boolean allowsNewReadOnly,
            @JsonProperty("allows_new_mutations") boolean allowsNewMutations) {
        public Scope {
            appIds = appIds == null ? List.of() : List.copyOf(appIds);
            capabilityIds = capabilityIds == null ? List.of() : List.copyOf(capabilityIds);
        }
    }

    public record Node(
            String id,
            String question,
            @JsonProperty("depends_on") List<String> dependsOn,
            Map<String, Object> inputs,
            Success success,
            String risk,
            String status,
            int attempt,
            @JsonProperty("selected_capability_id") String selectedCapabilityId,
            @JsonProperty("selected_tool") String selectedTool,
            Map<String, Object> output,
            String error) {
        public Node {
            dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
            inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
            output = output == null ? Map.of() : Map.copyOf(output);
        }
    }

    public record Success(
            @JsonProperty("required_outputs") List<String> requiredOutputs,
            @JsonProperty("semantic_condition") String semanticCondition) {
        public Success {
            requiredOutputs = requiredOutputs == null ? List.of() : List.copyOf(requiredOutputs);
        }
    }

    public record Edge(String from, String to, String type) {}
}
