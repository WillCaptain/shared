package org.twelve.aipp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Set;

/** Provider-neutral execution certainty. Transport completion is not task completion. */
public final class ExecutionOutcome {
    public static final String SCHEMA = "aipp.execution-outcome/v1";
    private static final Set<String> STATES = Set.of("not_started", "completed", "failed", "unknown");
    private ExecutionOutcome() {}

    public static Map<String, Object> unknownResult(String error, String message) {
        return Map.of("ok", false, "error", error, "message", message,
                "execution", Map.of("schema", SCHEMA, "state", "unknown", "retry_safe", false));
    }

    /** Fail closed for malformed/new outcome versions; legacy results retain their existing policy. */
    public static boolean allowsSuccess(JsonNode result) {
        if (result == null || !result.has("execution")) return true;
        JsonNode outcome = result.path("execution");
        return valid(outcome) && "completed".equals(outcome.path("state").asText());
    }

    public static boolean valid(JsonNode outcome) {
        return outcome != null && outcome.isObject()
                && SCHEMA.equals(outcome.path("schema").asText())
                && STATES.contains(outcome.path("state").asText())
                && outcome.path("retry_safe").isBoolean()
                && (!"unknown".equals(outcome.path("state").asText()) || !outcome.path("retry_safe").asBoolean());
    }
}
