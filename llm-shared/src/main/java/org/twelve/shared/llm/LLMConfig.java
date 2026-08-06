package org.twelve.shared.llm;

import java.util.Objects;

/**
 * Immutable LLM connection configuration (API key, base URL, model, timeout).
 *
 * <p>Pure value object with no global state. Attach to a world, agent, or service instance.
 */
public final class LLMConfig {

    public static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com/v1";
    /** Current DeepSeek default chat model (non-thinking). */
    public static final String DEEPSEEK_CHAT     = "deepseek-v4-flash";
    /**
     * Legacy alias kept for callers that still store the pre-deprecation id.
     * Prefer {@link #DEEPSEEK_CHAT}; DeepSeek maps the old name to non-thinking flash.
     */
    public static final String DEEPSEEK_CHAT_LEGACY = "deepseek-chat";
    /** Legacy reasoner id; prefer {@link #DEEPSEEK_CHAT} with thinking enabled at the API. */
    public static final String DEEPSEEK_REASONER = "deepseek-reasoner";

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int    timeoutSeconds;

    private LLMConfig(String apiKey, String baseUrl, String model, int timeoutSeconds) {
        this.apiKey         = Objects.requireNonNull(apiKey,   "apiKey");
        this.baseUrl        = Objects.requireNonNull(baseUrl,  "baseUrl");
        this.model          = Objects.requireNonNull(model,    "model");
        this.timeoutSeconds = timeoutSeconds;
    }

    public static LLMConfig of(String apiKey) {
        return new LLMConfig(apiKey, DEEPSEEK_BASE_URL, DEEPSEEK_CHAT, 30);
    }

    /** Alias for {@link #of(String)} — DeepSeek Chat defaults. */
    public static LLMConfig deepseek(String apiKey) {
        return of(apiKey);
    }

    public static LLMConfig of(String apiKey, String baseUrl, String model) {
        return new LLMConfig(apiKey, baseUrl, model, 30);
    }

    public static Builder builder() { return new Builder(); }

    public String apiKey()         { return apiKey; }
    public String baseUrl()        { return baseUrl; }
    public String model()          { return model; }
    public int    timeoutSeconds() { return timeoutSeconds; }

    public boolean hasKey() { return apiKey != null && !apiKey.isBlank(); }

    /** Copy with a different API key (pool assignment). */
    public LLMConfig withApiKey(String key) {
        return new LLMConfig(key == null ? "" : key, baseUrl, model, timeoutSeconds);
    }

    /** Copy with a different model id (construct/repair/verifier overrides). */
    public LLMConfig withModel(String model) {
        return new LLMConfig(apiKey, baseUrl, model == null ? this.model : model, timeoutSeconds);
    }

    public String chatCompletionsUrl() {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/chat/completions";
    }

    /** OpenAI-compatible embeddings endpoint derived from {@link #baseUrl()}. */
    public String embeddingsUrl() {
        return embeddingsUrl(baseUrl);
    }

    /** Normalize an OpenAI-compatible base URL into a concrete {@code /embeddings} endpoint. */
    public static String embeddingsUrl(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.strip();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.isBlank()) throw new IllegalArgumentException("embedding base URL is blank");
        if (base.endsWith("/embeddings")) return base;
        return base.endsWith("/v1") ? base + "/embeddings" : base + "/v1/embeddings";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LLMConfig c)) return false;
        return timeoutSeconds == c.timeoutSeconds
                && apiKey.equals(c.apiKey)
                && baseUrl.equals(c.baseUrl)
                && model.equals(c.model);
    }

    @Override public int hashCode() {
        return Objects.hash(apiKey, baseUrl, model, timeoutSeconds);
    }

    @Override public String toString() {
        String masked = apiKey.length() > 6
                ? apiKey.substring(0, 3) + "***" + apiKey.substring(apiKey.length() - 3)
                : "***";
        return "LLMConfig{baseUrl='" + baseUrl + "', model='" + model
                + "', timeout=" + timeoutSeconds + "s, apiKey='" + masked + "'}";
    }

    public static final class Builder {

        private String apiKey         = "";
        private String baseUrl        = DEEPSEEK_BASE_URL;
        private String model          = DEEPSEEK_CHAT;
        private int    timeoutSeconds = 30;

        private Builder() {}

        public Builder apiKey(String key)          { this.apiKey = Objects.requireNonNull(key); return this; }
        public Builder baseUrl(String url)         { this.baseUrl = Objects.requireNonNull(url); return this; }
        public Builder model(String model)         { this.model = Objects.requireNonNull(model); return this; }
        public Builder timeoutSeconds(int timeout) { this.timeoutSeconds = timeout; return this; }

        public LLMConfig build() {
            return new LLMConfig(apiKey, baseUrl, model, timeoutSeconds);
        }
    }
}
