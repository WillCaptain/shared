package org.twelve.aipp.collaboration;

import java.util.List;
import java.util.Map;

/** A side-effect-free, revision-bound proposal for a collaboration operation. */
public record CollaborationActionIntent(
        String schema,
        String actionType,
        String sessionId,
        String topicId,
        List<String> resourceRefs,
        List<String> targetMembers,
        String requestedBy,
        String audience,
        long expectedRevision,
        String idempotencyKey,
        Map<String, Object> parameters) {

    public static final String SCHEMA = "shared.collaboration-action-intent/v1";
    public static final List<String> ACTION_TYPES = List.of(
            "topic", "sting", "users", "files", "graph", "stickers");
    public static final List<String> AUDIENCES = List.of("room", "my_agent");

    public CollaborationActionIntent {
        if (!SCHEMA.equals(schema)) throw new IllegalArgumentException("unsupported collaboration intent schema");
        // Legacy alias from early drafts; normalize to the v1 action type.
        if ("task_tracking".equals(actionType)) actionType = "sting";
        if (!ACTION_TYPES.contains(actionType)) throw new IllegalArgumentException("unsupported collaboration action type");
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("sessionId is required");
        if (requestedBy == null || requestedBy.isBlank()) throw new IllegalArgumentException("requestedBy is required");
        if (!AUDIENCES.contains(audience)) throw new IllegalArgumentException("unsupported collaboration audience");
        if (expectedRevision < 0) throw new IllegalArgumentException("expectedRevision must be non-negative");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
        resourceRefs = resourceRefs == null ? List.of() : List.copyOf(resourceRefs);
        targetMembers = targetMembers == null ? List.of() : List.copyOf(targetMembers);
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
