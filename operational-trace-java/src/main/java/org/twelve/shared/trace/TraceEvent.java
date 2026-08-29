package org.twelve.shared.trace;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Operational trace event schema v0. */
public record TraceEvent(
        int schemaVersion,
        String traceId,
        String parentId,
        String correlationId,
        String userId,
        Instant ts,
        String actor,
        String surface,
        String action,
        Map<String, Object> target,
        Map<String, Object> request,
        Map<String, Object> response,
        String outcome
) {
    public static final int SCHEMA_VERSION = 0;

    private static final ObjectMapper JSON = new ObjectMapper();

    public TraceEvent {
        if (schemaVersion < 0) throw new IllegalArgumentException("schemaVersion");
        if (traceId == null || traceId.isBlank()) throw new IllegalArgumentException("traceId");
        if (correlationId == null || correlationId.isBlank()) throw new IllegalArgumentException("correlationId");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId");
        if (ts == null) ts = Instant.now();
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("actor");
        if (surface == null || surface.isBlank()) throw new IllegalArgumentException("surface");
        if (action == null || action.isBlank()) throw new IllegalArgumentException("action");
        if (outcome == null || outcome.isBlank()) throw new IllegalArgumentException("outcome");
        target = PayloadSanitizer.sanitizeMap(target == null ? Map.of() : target);
        request = PayloadSanitizer.sanitizeMap(request == null ? Map.of() : request);
        response = PayloadSanitizer.sanitizeMap(response == null ? Map.of() : response);
    }

    public static TraceEvent of(String userId,
                                String actor,
                                String surface,
                                String action,
                                String correlationId,
                                String outcome) {
        return new TraceEvent(SCHEMA_VERSION, TraceIds.newId(), null, correlationId, userId,
                Instant.now(), actor, surface, action, Map.of(), Map.of(), Map.of(), outcome);
    }

    public TraceEvent withParent(String parentId) {
        return new TraceEvent(schemaVersion, traceId, parentId, correlationId, userId, ts,
                actor, surface, action, target, request, response, outcome);
    }

    public TraceEvent withTarget(Map<String, Object> target) {
        return new TraceEvent(schemaVersion, traceId, parentId, correlationId, userId, ts,
                actor, surface, action, target, request, response, outcome);
    }

    public TraceEvent withRequest(Map<String, Object> request) {
        return new TraceEvent(schemaVersion, traceId, parentId, correlationId, userId, ts,
                actor, surface, action, target, request, response, outcome);
    }

    public TraceEvent withResponse(Map<String, Object> response) {
        return new TraceEvent(schemaVersion, traceId, parentId, correlationId, userId, ts,
                actor, surface, action, target, request, response, outcome);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schema_version", schemaVersion);
        out.put("trace_id", traceId);
        if (parentId != null && !parentId.isBlank()) out.put("parent_id", parentId);
        out.put("correlation_id", correlationId);
        out.put("user_id", userId);
        out.put("ts", ts.toString());
        out.put("actor", actor);
        out.put("surface", surface);
        out.put("action", action);
        if (!target.isEmpty()) out.put("target", target);
        if (!request.isEmpty()) out.put("request", request);
        if (!response.isEmpty()) out.put("response", response);
        out.put("outcome", outcome);
        return Map.copyOf(out);
    }

    public static TraceEvent fromMap(Map<String, Object> raw) {
        Map<String, Object> source = raw == null ? Map.of() : raw;
        String traceId = firstString(source, "trace_id", "id");
        String correlationId = firstString(source, "correlation_id", "correlationId");
        String resolvedTraceId = textOr(traceId, TraceIds.newId());
        return new TraceEvent(
                intValue(first(source, "schema_version", "schemaVersion"), SCHEMA_VERSION),
                resolvedTraceId,
                blankToNull(firstString(source, "parent_id", "parentId")),
                textOr(correlationId, resolvedTraceId),
                textOr(firstString(source, "user_id", "userId"), "unknown"),
                instant(first(source, "ts", "timestamp")),
                textOr(firstString(source, "actor"), "system"),
                textOr(firstString(source, "surface"), TraceSurfaces.HOST),
                textOr(firstString(source, "action"), "unknown"),
                map(first(source, "target")),
                map(first(source, "request", "context")),
                map(first(source, "response", "metadata")),
                textOr(firstString(source, "outcome"), TraceOutcomes.OK));
    }

    public String toJsonLine() {
        try {
            return JSON.writeValueAsString(toMap());
        } catch (Exception ignored) {
            return "{\"error\":\"trace_serialize_failed\"}";
        }
    }

    /** Compatibility aliases for the first provisional operational trace artifact. */
    public String id() { return traceId; }
    public Instant timestamp() { return ts; }
    public Map<String, Object> context() { return request; }
    public Map<String, Object> metadata() { return response; }

    private static Object first(Map<String, Object> raw, String... keys) {
        for (String key : keys) if (raw.containsKey(key)) return raw.get(key);
        return null;
    }

    private static String firstString(Map<String, Object> raw, String... keys) {
        Object value = first(raw, keys);
        return value == null ? null : String.valueOf(value).trim();
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value != null && !String.valueOf(value).isBlank()) {
            try {
                return Instant.parse(String.valueOf(value));
            } catch (RuntimeException ignored) {
                // Fall through to a safe ingest timestamp.
            }
        }
        return Instant.now();
    }

    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        source.forEach((key, item) -> out.put(String.valueOf(key), item));
        return out;
    }

    private static String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
