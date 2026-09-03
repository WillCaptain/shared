package org.twelve.aipp.collaboration;

import java.time.Instant;
import java.util.List;

/** Short-lived, actor-bound proof that a resource provider can verify offline. */
public record ResourceGrantAssertion(
        String schema, String issuer, String grantId, String conversationId,
        String ownerId, String actorId, String provider, String resourceId,
        String versionId, String digest, String operation, List<String> operations,
        Instant issuedAt, Instant expiresAt, String nonce) {

    public static final String SCHEMA = "shared.resource-grant-assertion/v1";

    public ResourceGrantAssertion {
        if (!SCHEMA.equals(schema)) throw new IllegalArgumentException("unsupported grant assertion schema");
        if (blank(issuer) || blank(grantId) || blank(conversationId) || blank(ownerId)
                || blank(actorId) || blank(provider) || blank(resourceId) || blank(versionId)
                || blank(digest) || blank(operation) || blank(nonce)) {
            throw new IllegalArgumentException("grant assertion is incomplete");
        }
        operations = operations == null ? List.of() : List.copyOf(operations);
        if (!operations.contains(operation)) throw new IllegalArgumentException("operation is not granted");
        if (issuedAt == null || expiresAt == null || !expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("grant assertion lifetime is invalid");
        }
    }

    public boolean validFor(String expectedActor, String expectedOperation, Instant now) {
        return actorId.equals(expectedActor) && operation.equals(expectedOperation)
                && now != null && !now.isBefore(issuedAt.minusSeconds(30)) && now.isBefore(expiresAt);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
