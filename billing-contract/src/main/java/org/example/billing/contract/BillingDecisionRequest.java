package org.example.billing.contract;

import java.time.Instant;

/** Call facts supplied by World-One. Payer, billing and pricing fields are intentionally absent. */
public record BillingDecisionRequest(
        String idempotencyKey,
        String callId,
        String declaredUserId,
        String operation,
        String featureCode,
        String provider,
        String model,
        CredentialSource credentialSource,
        Long estimatedExposure,
        Instant requestedAt) {

    public BillingDecisionRequest {
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        callId = required(callId, "callId");
        declaredUserId = required(declaredUserId, "declaredUserId");
        operation = required(operation, "operation");
        featureCode = clean(featureCode);
        provider = required(provider, "provider");
        model = required(model, "model");
        credentialSource = credentialSource == null ? CredentialSource.UNKNOWN : credentialSource;
        estimatedExposure = estimatedExposure == null ? 0L : Math.max(0L, estimatedExposure);
        requestedAt = requestedAt == null ? Instant.now() : requestedAt;
    }

    private static String required(String value, String name) {
        String normalized = clean(value);
        if (normalized == null) throw new IllegalArgumentException(name + " is required");
        return normalized;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
