package org.twelve.aipp.collaboration;

import java.time.Instant;
import java.util.List;

/** Explicit recipient/action/time scope for access to one immutable resource version. */
public record ScopedResourceGrant(
        String schema,
        String grantId,
        String conversationId,
        String ownerId,
        List<String> recipientIds,
        String resourceId,
        String versionId,
        List<String> operations,
        Instant expiresAt,
        Instant revokedAt,
        String confirmationId) {

    public static final String SCHEMA = "shared.scoped-resource-grant/v1";
    public static final List<String> ALLOWED_OPERATIONS = List.of("view", "download", "comment", "propose");

    public ScopedResourceGrant {
        if (!SCHEMA.equals(schema)) throw new IllegalArgumentException("unsupported resource grant schema");
        if (grantId == null || grantId.isBlank()) throw new IllegalArgumentException("grantId is required");
        if (conversationId == null || conversationId.isBlank()) throw new IllegalArgumentException("conversationId is required");
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId is required");
        recipientIds = recipientIds == null ? List.of() : List.copyOf(recipientIds);
        if (recipientIds.isEmpty()) throw new IllegalArgumentException("at least one recipient is required");
        if (resourceId == null || resourceId.isBlank() || versionId == null || versionId.isBlank()) {
            throw new IllegalArgumentException("resourceId and versionId are required");
        }
        operations = operations == null ? List.of() : List.copyOf(operations);
        if (operations.isEmpty() || !ALLOWED_OPERATIONS.containsAll(operations)) {
            throw new IllegalArgumentException("unsupported or empty operations");
        }
        if (expiresAt == null) throw new IllegalArgumentException("expiresAt is required");
    }

    public boolean activeAt(Instant now) {
        return revokedAt == null && now != null && now.isBefore(expiresAt);
    }
}
