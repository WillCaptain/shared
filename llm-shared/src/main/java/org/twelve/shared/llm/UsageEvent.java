package org.twelve.shared.llm;

import java.time.Instant;
import java.util.Objects;

/** Immutable domain event emitted after a real provider invocation completes or partially reports usage. */
public record UsageEvent(
        String eventId,
        String userId,
        String sessionId,
        String featureCode,
        String billingMode,
        boolean billable,
        boolean callSucceeded,
        Usage usage,
        Instant occurredAt) {

    public UsageEvent {
        eventId = Objects.requireNonNull(eventId, "eventId");
        usage = Objects.requireNonNull(usage, "usage");
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        userId = blankToNull(userId);
        sessionId = blankToNull(sessionId);
        featureCode = blankToNull(featureCode);
        billingMode = billingMode == null || billingMode.isBlank() ? "unknown" : billingMode.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
