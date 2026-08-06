package org.twelve.aipp;

import com.fasterxml.jackson.databind.JsonNode;
import org.twelve.aipp.planning.FreePlanDag;
import org.twelve.aipp.planning.GoalCompilation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Executable protocol validator for Goal Compilation v1 and Free Plan DAG v2. */
public final class AippFreePlanSpec {

    private static final Set<String> MODES = Set.of("direct", "dag", "clarify");
    private static final Set<String> PLAN_STATUSES = Set.of(
            "draft", "resolving", "awaiting_approval", "running", "needs_replan",
            "succeeded", "failed", "blocked", "blocked_unknown_mutation", "invalid");
    private static final Set<String> NODE_STATUSES = Set.of(
            "pending", "ready", "awaiting_approval", "running", "succeeded", "failed",
            "needs_replan", "unknown_mutation_state", "blocked", "skipped");
    private static final Set<String> RISKS = Set.of("read_only", "mutation", "unknown");

    public void assertValidGoalCompilation(JsonNode root) {
        requireObject(root, "goal compilation");
        requireEquals(root, "schema_version", GoalCompilation.SCHEMA_VERSION);
        String mode = requireText(root, "mode");
        require(MODES.contains(mode), "mode must be direct, dag, or clarify");
        requireText(root, "normalized_goal");
        if (root.has("constraints")) requireStringArray(root.get("constraints"), "constraints");
        switch (mode) {
            case "direct" -> {
                JsonNode query = root.get("direct_query");
                requireObject(query, "direct_query");
                requireText(query, "primary");
                if (query.has("alternates")) requireStringArray(query.get("alternates"), "direct_query.alternates");
                require(!root.hasNonNull("dag"), "direct compilation must not contain dag");
            }
            case "dag" -> assertValidFreePlanDag(root.get("dag"), 50, 100, 20);
            case "clarify" -> {
                requireText(root, "question");
                requireStringArray(root.get("missing"), "missing");
                require(!root.hasNonNull("dag"), "clarify compilation must not contain dag");
            }
            default -> throw new IllegalArgumentException("unsupported mode");
        }
    }

    public void assertValidFreePlanDag(JsonNode root) {
        assertValidFreePlanDag(root, 50, 100, 20);
    }

    public void assertValidFreePlanDag(JsonNode root, int maxNodes, int maxEdges, int maxDepth) {
        requireObject(root, "free plan");
        requireEquals(root, "schema_version", FreePlanDag.SCHEMA_VERSION);
        requireText(root, "plan_id");
        require(root.path("revision").canConvertToInt() && root.path("revision").asInt() >= 1,
                "revision must be >= 1");
        requireText(root, "goal");
        String status = requireText(root, "status");
        require(PLAN_STATUSES.contains(status), "invalid plan status: " + status);
        requireObject(root.get("scope"), "scope");
        JsonNode nodesNode = root.get("nodes");
        require(nodesNode != null && nodesNode.isArray() && nodesNode.size() >= 2,
                "nodes must contain at least two entries");
        require(nodesNode.size() <= positive(maxNodes, 50), "node limit exceeded");
        JsonNode edgesNode = root.get("edges");
        require(edgesNode != null && edgesNode.isArray(), "edges must be an array");
        require(edgesNode.size() <= positive(maxEdges, 100), "edge limit exceeded");

        Map<String, JsonNode> nodes = new LinkedHashMap<>();
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        for (JsonNode node : nodesNode) {
            requireObject(node, "node");
            String id = requireText(node, "id");
            require(!nodes.containsKey(id), "duplicate node id: " + id);
            requireText(node, "question");
            JsonNode depends = node.get("depends_on");
            requireStringArray(depends, "node.depends_on");
            Set<String> deps = new HashSet<>();
            depends.forEach(v -> deps.add(v.asText()));
            require(!deps.contains(id), "self dependency: " + id);
            requireObject(node.get("inputs"), "node.inputs");
            requireObject(node.get("success"), "node.success");
            if (node.path("success").has("required_outputs")) {
                requireStringArray(node.path("success").get("required_outputs"), "success.required_outputs");
            }
            String nodeStatus = requireText(node, "status");
            require(NODE_STATUSES.contains(nodeStatus), "invalid node status: " + nodeStatus);
            String risk = requireText(node, "risk");
            require(RISKS.contains(risk), "invalid node risk: " + risk);
            require(node.path("attempt").canConvertToInt() && node.path("attempt").asInt() >= 0,
                    "attempt must be >= 0");
            nodes.put(id, node);
            dependencies.put(id, deps);
        }
        dependencies.forEach((id, deps) -> deps.forEach(dep ->
                require(nodes.containsKey(dep), "unknown dependency " + dep + " for " + id)));
        require(!hasCycle(dependencies), "graph contains cycle");
        require(graphDepth(dependencies) <= positive(maxDepth, 20), "graph depth limit exceeded");

        for (JsonNode edge : edgesNode) {
            requireObject(edge, "edge");
            String from = requireText(edge, "from");
            String to = requireText(edge, "to");
            require(nodes.containsKey(from) && nodes.containsKey(to), "edge references unknown node");
            require(dependencies.get(to).contains(from),
                    "edge must agree with target depends_on: " + from + " -> " + to);
        }
        for (Map.Entry<String, JsonNode> entry : nodes.entrySet()) {
            validateBindings(entry.getKey(), entry.getValue().path("inputs"), nodes, dependencies);
        }
    }

    public void assertValidSysPlanPayload(JsonNode data) {
        requireObject(data, "sys.plan data");
        requireEquals(data, "schema_version", FreePlanDag.SCHEMA_VERSION);
        requireText(data, "plan_id");
        require(data.path("revision").canConvertToInt() && data.path("revision").asInt() >= 1,
                "sys.plan revision must be >= 1");
        require(data.path("nodes").isArray(), "sys.plan nodes must be an array");
        require(data.path("edges").isArray(), "sys.plan edges must be an array");
        require(data.has("requires_approval") && data.get("requires_approval").isBoolean(),
                "sys.plan requires_approval must be boolean");
        require(data.has("can_execute") && data.get("can_execute").isBoolean(),
                "sys.plan can_execute must be boolean");
    }

    private static void validateBindings(
            String owner,
            JsonNode value,
            Map<String, JsonNode> nodes,
            Map<String, Set<String>> dependencies) {
        if (value == null) return;
        if (value.isObject() && value.has("from_node")) {
            String source = requireText(value, "from_node");
            require(nodes.containsKey(source), "binding references unknown node: " + source);
            require(!requireText(value, "output").isBlank(), "binding output required");
            require(isAncestor(source, owner, dependencies),
                    "binding source must be a dependency ancestor: " + source + " -> " + owner);
            return;
        }
        if (value.isContainerNode()) value.forEach(child ->
                validateBindings(owner, child, nodes, dependencies));
    }

    private static boolean isAncestor(String candidate, String node, Map<String, Set<String>> deps) {
        if (deps.getOrDefault(node, Set.of()).contains(candidate)) return true;
        for (String parent : deps.getOrDefault(node, Set.of())) {
            if (isAncestor(candidate, parent, deps)) return true;
        }
        return false;
    }

    private static boolean hasCycle(Map<String, Set<String>> deps) {
        Map<String, Integer> state = new HashMap<>();
        for (String id : deps.keySet()) if (visit(id, deps, state)) return true;
        return false;
    }

    private static boolean visit(String id, Map<String, Set<String>> deps, Map<String, Integer> state) {
        int current = state.getOrDefault(id, 0);
        if (current == 1) return true;
        if (current == 2) return false;
        state.put(id, 1);
        for (String dep : deps.getOrDefault(id, Set.of())) {
            if (visit(dep, deps, state)) return true;
        }
        state.put(id, 2);
        return false;
    }

    private static int graphDepth(Map<String, Set<String>> deps) {
        Map<String, Integer> memo = new HashMap<>();
        int max = 0;
        for (String id : deps.keySet()) max = Math.max(max, depth(id, deps, memo));
        return max;
    }

    private static int depth(String id, Map<String, Set<String>> deps, Map<String, Integer> memo) {
        if (memo.containsKey(id)) return memo.get(id);
        int value = 1;
        for (String dep : deps.getOrDefault(id, Set.of())) {
            value = Math.max(value, 1 + depth(dep, deps, memo));
        }
        memo.put(id, value);
        return value;
    }

    private static void requireObject(JsonNode node, String label) {
        require(node != null && node.isObject(), label + " must be an object");
    }

    private static String requireText(JsonNode node, String field) {
        require(node != null && node.has(field) && node.get(field).isTextual()
                        && !node.get(field).asText().isBlank(),
                field + " must be a non-blank string");
        return node.get(field).asText();
    }

    private static void requireEquals(JsonNode node, String field, String expected) {
        require(expected.equals(requireText(node, field)), field + " must be " + expected);
    }

    private static void requireStringArray(JsonNode node, String label) {
        require(node != null && node.isArray(), label + " must be an array");
        node.forEach(value -> require(value.isTextual() && !value.asText().isBlank(),
                label + " entries must be non-blank strings"));
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
