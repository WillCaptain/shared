package org.twelve.shared.llm;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;

/** Standardized provider usage attached to an {@link LLMCaller.LLMResponse}. */
public record Usage(
        String provider,
        String model,
        String callId,
        String turnId,
        String callType,
        Long inputTokens,
        Long cachedInputTokens,
        Long uncachedInputTokens,
        Long outputTokens,
        String usageStatus,
        String rawUsageJson) {

    public static final String REPORTED = "reported";
    public static final String ESTIMATED = "estimated";
    public static final String UNKNOWN = "unknown";

    public Usage {
        provider = blankToDefault(provider, "unknown");
        model = blankToDefault(model, "unknown");
        callId = blankToNull(callId);
        turnId = blankToNull(turnId);
        callType = blankToDefault(callType, "unknown");
        usageStatus = blankToDefault(usageStatus, UNKNOWN);
        inputTokens = nonNegativeOrNull(inputTokens);
        cachedInputTokens = nonNegativeOrNull(cachedInputTokens);
        uncachedInputTokens = nonNegativeOrNull(uncachedInputTokens);
        outputTokens = nonNegativeOrNull(outputTokens);
    }

    public static Usage unknown(String provider, String model) {
        return new Usage(provider, model, null, null, "unknown",
                null, null, null, null, UNKNOWN, null);
    }

    public Usage withIdentity(String callId, String turnId, String callType) {
        return new Usage(provider, model, callId, turnId, callType,
                inputTokens, cachedInputTokens, uncachedInputTokens, outputTokens,
                usageStatus, rawUsageJson);
    }

    @JsonIgnore
    public boolean isReported() {
        return REPORTED.equals(usageStatus);
    }

    private static Long nonNegativeOrNull(Long value) {
        return value == null ? null : Math.max(0L, value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String blankToDefault(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized == null ? Objects.requireNonNull(fallback) : normalized;
    }
}
