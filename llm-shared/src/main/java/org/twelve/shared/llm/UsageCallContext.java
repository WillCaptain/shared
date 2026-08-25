package org.twelve.shared.llm;

import java.util.Objects;

/**
 * Correlation and billing metadata for one model invocation.
 *
 * <p>This type deliberately contains no Credit or persistence concepts. Hosts may use it to
 * attach identity and feature metadata to the Usage Event emitted by {@link LLMCaller}.
 */
public record UsageCallContext(
        String userId,
        String sessionId,
        String turnId,
        String callType,
        String featureCode,
        String billingMode,
        boolean billable) {

    public UsageCallContext {
        userId = blankToNull(userId);
        sessionId = blankToNull(sessionId);
        turnId = blankToNull(turnId);
        callType = blankToDefault(callType, "unknown");
        featureCode = blankToNull(featureCode);
        billingMode = blankToDefault(billingMode, "unknown");
    }

    public static UsageCallContext none() {
        return new UsageCallContext(null, null, null, "unknown", null, "unknown", false);
    }

    /** Metadata for a normal Host-owned model call. The Host need not know Credit internals. */
    public static UsageCallContext platformCall(String userId, String sessionId, String turnId,
                                                String callType, String featureCode) {
        return new UsageCallContext(userId, sessionId, turnId, callType, featureCode,
                "platform", true);
    }

    public UsageCallContext withCallType(String nextCallType) {
        return new UsageCallContext(userId, sessionId, turnId, nextCallType,
                featureCode, billingMode, billable);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String blankToDefault(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized == null ? Objects.requireNonNull(fallback) : normalized;
    }
}
