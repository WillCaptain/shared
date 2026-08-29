package org.example.billing.contract;

/** Immutable normalized Provider usage. Null token counts mean the Provider did not report usage. */
public record BillingUsage(
        Long inputTokens,
        Long cachedInputTokens,
        Long uncachedInputTokens,
        Long outputTokens,
        Long reasoningTokens,
        String usageStatus,
        String rawProviderUsage) {

    public static final String REPORTED = "reported";
    public static final String UNKNOWN = "unknown";

    public boolean reported() {
        return REPORTED.equalsIgnoreCase(usageStatus)
                && nonNegative(inputTokens)
                && nonNegative(cachedInputTokens)
                && nonNegative(uncachedInputTokens)
                && nonNegative(outputTokens)
                && nonNegative(reasoningTokens == null ? 0L : reasoningTokens);
    }

    private static boolean nonNegative(Long value) {
        return value != null && value >= 0;
    }
}
