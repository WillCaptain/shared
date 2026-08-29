package org.example.billing.contract;

import java.time.Instant;
import java.util.Set;

/** Metering fact sent at least once from World-One to Billing-One. */
public record BillingUsageEvent(
        String eventId,
        String decisionId,
        String callId,
        String invocationId,
        String serviceIdentity,
        String originService,
        String subjectUserId,
        String operation,
        String featureCode,
        String provider,
        String model,
        BillingUsage usage,
        String usageStatus,
        boolean callSucceeded,
        boolean interrupted,
        String rawProviderUsage,
        Instant callStartedAt,
        Instant occurredAt) {

    public BillingUsageEvent {
        eventId = bounded(eventId, "eventId", 64);
        decisionId = required(decisionId, "decisionId");
        callId = required(callId, "callId");
        invocationId = bounded(invocationId, "invocationId", 64);
        serviceIdentity = required(serviceIdentity, "serviceIdentity");
        originService = originService == null || originService.isBlank() ? serviceIdentity : originService.strip();
        if (!Set.of("world-one", "note-one", "memory-one").contains(originService))
            throw new IllegalArgumentException("originService must be world-one, note-one or memory-one");
        operation = required(operation, "operation");
        provider = required(provider, "provider");
        model = required(model, "model");
        usageStatus = required(usageStatus, "usageStatus");
        callStartedAt = callStartedAt == null ? Instant.now() : callStartedAt;
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    /** Compatibility constructor for callers that predate the explicit origin/invocation fields. */
    public BillingUsageEvent(String eventId, String decisionId, String callId, String serviceIdentity,
                             String subjectUserId, String operation, String featureCode, String provider,
                             String model, BillingUsage usage, String usageStatus, boolean callSucceeded,
                             boolean interrupted, String rawProviderUsage, Instant callStartedAt,
                             Instant occurredAt) {
        this(eventId, decisionId, callId, callId, serviceIdentity, serviceIdentity, subjectUserId, operation,
                featureCode, provider, model, usage, usageStatus, callSucceeded, interrupted, rawProviderUsage,
                callStartedAt, occurredAt);
    }

    public boolean reported() {
        return BillingUsage.REPORTED.equalsIgnoreCase(usageStatus) && usage != null && usage.reported();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.strip();
    }

    private static String bounded(String value, String name, int maxLength) {
        String normalized = required(value, name);
        if (normalized.length() > maxLength)
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        return normalized;
    }
}
