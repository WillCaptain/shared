package org.twelve.aipp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;

/** Provider-declared, data-only result projection. Never changes the authoritative tool receipt. */
public final class AgentResultPolicy {
    public static final String FIELD = "agent_result";
    private static final ObjectMapper JSON = new ObjectMapper();
    private AgentResultPolicy() {}
    public record Result(String content, boolean evidenceReady) {}

    public static Result apply(Map<String, Object> tool, String raw) {
        Result unchanged = new Result(raw, false);
        if (raw == null || tool == null || !(tool.get(FIELD) instanceof Map<?, ?> policy)) return unchanged;
        try {
            JsonNode root = JSON.readTree(raw);
            if (root == null || !root.isObject() || root.path("ok").isBoolean() && !root.path("ok").asBoolean())
                return unchanged;
            String status = root.path("status").asText("");
            if (!List.of("", "ok", "success", "completed").contains(status)) return unchanged;
            if (root.hasNonNull("error")) return unchanged;
            // Presentation and pause envelopes are authoritative; never compact them into prose.
            for (String field : List.of("html_widget", "canvas", "pop_widget", "new_session",
                    "missing_params", "awaiting_confirmation", "awaiting_selection")) {
                if (root.hasNonNull(field)) return unchanged;
            }
            if (!(policy.get("evidence_paths") instanceof List<?> paths)
                    || paths.isEmpty() || paths.size() > 16) return unchanged;
            for (Object path : paths) {
                if (!(path instanceof String p) || !p.startsWith("/")) return unchanged;
                JsonNode value = root.at(p);
                if (value.isMissingNode() || value.isNull() || value.isContainerNode() && value.isEmpty()) return unchanged;
            }
            JsonNode projection = project(root, policy.get("projection"), 0);
            if (projection == null || projection.isEmpty()) return unchanged;
            String compact = JSON.writeValueAsString(projection);
            if (compact.length() > 16000) return unchanged;
            return new Result(compact, Boolean.TRUE.equals(policy.get("synthesize")));
        } catch (Exception malformed) {
            return unchanged;
        }
    }

    private static JsonNode project(JsonNode source, Object spec, int depth) {
        if (depth > 8) throw new IllegalArgumentException("projection too deep");
        if (spec instanceof String pointer) {
            if (!pointer.startsWith("/")) throw new IllegalArgumentException("expected JSON pointer");
            return source.at(pointer).deepCopy();
        }
        if (!(spec instanceof Map<?, ?> fields) || fields.size() > 64)
            throw new IllegalArgumentException("invalid projection");
        if (fields.containsKey("items") && fields.containsKey("path")) {
            JsonNode array = source.at(String.valueOf(fields.get("path")));
            if (!array.isArray()) return JSON.createArrayNode();
            int limit = fields.get("limit") instanceof Number n ? n.intValue() : 10;
            if (limit < 1 || limit > 32) throw new IllegalArgumentException("invalid projection limit");
            var out = JSON.createArrayNode();
            for (int i = 0; i < Math.min(limit, array.size()); i++)
                out.add(project(array.get(i), fields.get("items"), depth + 1));
            return out;
        }
        ObjectNode out = JSON.createObjectNode();
        for (var field : fields.entrySet()) {
            if (!(field.getKey() instanceof String key) || key.isBlank()) continue;
            JsonNode value = project(source, field.getValue(), depth + 1);
            if (value != null && !value.isMissingNode() && !value.isNull()) out.set(key, value);
        }
        return out;
    }
}
