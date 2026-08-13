package org.twelve.shared.trace;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Operational trace event schema v0.
 *
 * @see shared/aipp-protocol/spec/trace.md
 */
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
        return new TraceEvent(
                SCHEMA_VERSION,
                TraceIds.newId(),
                null,
                correlationId,
                userId,
                Instant.now(),
                actor,
                surface,
                action,
                Map.of(),
                Map.of(),
                Map.of(),
                outcome);
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
        if (target != null && !target.isEmpty()) out.put("target", target);
        if (request != null && !request.isEmpty()) out.put("request", request);
        if (response != null && !response.isEmpty()) out.put("response", response);
        out.put("outcome", outcome);
        return out;
    }

    @SuppressWarnings("unchecked")
    public static TraceEvent fromMap(Map<String, Object> raw) {
        Map<String, Object> m = raw == null ? Map.of() : raw;
        Instant ts = Instant.now();
        Object tsRaw = m.get("ts");
        if (tsRaw != null) {
            try {
                ts = Instant.parse(String.valueOf(tsRaw));
            } catch (Exception ignored) {
                // keep now
            }
        }
        return new TraceEvent(
                intVal(m.get("schema_version"), SCHEMA_VERSION),
                str(m.get("trace_id"), TraceIds.newId()),
                strOrNull(m.get("parent_id")),
                str(m.get("correlation_id"), TraceIds.newId()),
                str(m.get("user_id"), "unknown"),
                ts,
                str(m.get("actor"), "system"),
                str(m.get("surface"), TraceSurfaces.HOST),
                str(m.get("action"), "unknown"),
                map(m.get("target")),
                map(m.get("request")),
                map(m.get("response")),
                str(m.get("outcome"), TraceOutcomes.OK));
    }

    public String toJsonLine() {
        try {
            return JSON.writeValueAsString(toMap());
        } catch (Exception e) {
            return "{\"error\":\"trace_serialize_failed\"}";
        }
    }

    private static String str(Object v, String defaultValue) {
        if (v == null) return defaultValue;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? defaultValue : s;
    }

    private static String strOrNull(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static int intVal(Object v, int defaultValue) {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return defaultValue;
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object v) {
        if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return Map.of();
    }
}
