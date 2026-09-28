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
        Instant requestedAt,
        Long inputTokenEstimate,
        Integer requestedMaxOutputTokens) {

    /** Call facts without a server-side token estimate. Billing prices only the fields it is given. */
    public BillingDecisionRequest(String idempotencyKey, String callId, String declaredUserId, String operation,
                                  String featureCode, String provider, String model, CredentialSource credentialSource,
                                  Long estimatedExposure, Instant requestedAt) {
        this(idempotencyKey, callId, declaredUserId, operation, featureCode, provider, model, credentialSource,
                estimatedExposure, requestedAt, null, null);
    }

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
        if (inputTokenEstimate != null && inputTokenEstimate < 0) inputTokenEstimate = 0L;
        if (requestedMaxOutputTokens != null && requestedMaxOutputTokens < 0) requestedMaxOutputTokens = 0;
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
