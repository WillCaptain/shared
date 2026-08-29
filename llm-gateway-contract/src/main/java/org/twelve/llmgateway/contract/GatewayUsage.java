package org.twelve.llmgateway.contract;

public record GatewayUsage(
        String provider, String model, Long inputTokens, Long cachedInputTokens,
        Long uncachedInputTokens, Long outputTokens, Long reasoningTokens,
        String status, String rawUsage, String adapterVersion) {
    public static final String REPORTED = "reported";
    public static final String UNKNOWN = "unknown";

    public GatewayUsage {
        status = status == null || status.isBlank() ? UNKNOWN : status.strip();
        adapterVersion = adapterVersion == null || adapterVersion.isBlank() ? "unknown" : adapterVersion.strip();
    }

    public boolean isConsistent() {
        if (!REPORTED.equals(status) || inputTokens == null || cachedInputTokens == null
                || uncachedInputTokens == null || outputTokens == null) return false;
        if (inputTokens < 0 || cachedInputTokens < 0 || uncachedInputTokens < 0 || outputTokens < 0) return false;
        return inputTokens == cachedInputTokens + uncachedInputTokens;
    }
}
