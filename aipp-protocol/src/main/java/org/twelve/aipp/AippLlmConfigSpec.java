package org.twelve.aipp;

import com.fasterxml.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Host LLM provider config — {@code GET /api/llm-config} and write endpoints.
 *
 * <p>See {@code spec/llm-config.md}.
 */
public class AippLlmConfigSpec {

    public static final String SOURCE_USER = "user";
    public static final String SOURCE_INSTANCE = "instance";
    public static final String SOURCE_ENV = "env";

    public void assertValidLlmConfigResponse(JsonNode response) {
        assertThat(response).as("[LLM config] response must not be null").isNotNull();
        assertThat(response.path("ok").isBoolean())
                .as("[LLM config] 'ok' must be boolean").isTrue();
        if (!response.path("ok").asBoolean(false)) {
            assertThat(response.path("error").asText(""))
                    .as("[LLM config] error response needs 'error'")
                    .isNotBlank();
            return;
        }
        String source = response.path("source").asText("");
        assertThat(source)
                .as("[LLM config] 'source' must be user|instance|env")
                .isIn(SOURCE_USER, SOURCE_INSTANCE, SOURCE_ENV);
        JsonNode config = response.get("config");
        assertThat(config).as("[LLM config] ok=true requires 'config' object").isNotNull();
        assertValidLlmConfigBlock(config, true);
    }

    public void assertValidLlmConfigInstancePutRequest(JsonNode body) {
        assertThat(body).as("[LLM config] PUT instance body must not be null").isNotNull();
        assertValidLlmConfigBlock(body, true);
    }

    public void assertValidLlmConfigUserPutRequest(JsonNode body) {
        assertThat(body).as("[LLM config] PUT user body must not be null").isNotNull();
        assertThat(body.path("user_id").asText(""))
                .as("[LLM config] PUT user requires non-blank user_id")
                .isNotBlank();
        assertValidLlmConfigBlock(body, true);
    }

    public void assertValidLlmConfigInstanceMaskedResponse(JsonNode response) {
        assertThat(response).as("[LLM config] masked response must not be null").isNotNull();
        assertThat(response.path("ok").asBoolean(false))
                .as("[LLM config] masked read expects ok=true").isTrue();
        JsonNode config = response.get("config");
        assertThat(config).isNotNull();
        assertThat(config.has("api_key_masked") || config.has("api_key"))
                .as("[LLM config] masked config needs api_key_masked or api_key")
                .isTrue();
        assertThat(config.path("base_url").asText("")).isNotBlank();
        assertThat(config.path("model").asText("")).isNotBlank();
    }

    private void assertValidLlmConfigBlock(JsonNode block, boolean requireApiKey) {
        if (requireApiKey) {
            String key = firstNonBlank(
                    block.path("api_key").asText(""),
                    block.path("apiKey").asText(""));
            assertThat(key).as("[LLM config] api_key required").isNotBlank();
        }
        String baseUrl = firstNonBlank(
                block.path("base_url").asText(""),
                block.path("baseUrl").asText(""));
        assertThat(baseUrl).as("[LLM config] base_url required").isNotBlank();
        assertThat(block.path("model").asText(""))
                .as("[LLM config] model required")
                .isNotBlank();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b == null ? "" : b;
    }
}
