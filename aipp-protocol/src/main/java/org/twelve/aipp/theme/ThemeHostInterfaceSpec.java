package org.twelve.aipp.theme;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Public contract between an AIPP that owns themes and a generic AIPP Host.
 *
 * <p>The Host treats the payload as opaque domain data. The browser implementation
 * supplied by the theme owner performs the full descriptor validation and projection.
 */
public final class ThemeHostInterfaceSpec {

    public static final String EFFECT_TYPE = "shared.theme.apply/v1";
    public static final int SCHEMA_VERSION = 1;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern PACKAGE_ID = Pattern.compile(
            "[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?"
                    + "(?:\\.[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?){1,7}");
    private static final Pattern SEMVER = Pattern.compile(
            "(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)"
                    + "(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?"
                    + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?");
    private static final Pattern INSTANCE_ID = Pattern.compile("[0-9a-f]{24}");

    public Map<String, Object> effect(Map<String, Object> payload) {
        assertValidPayload(JSON.valueToTree(payload));
        return Map.of("type", EFFECT_TYPE, "payload", payload);
    }

    public void assertValidEffect(JsonNode effect) {
        requireObject(effect, "theme Host effect");
        require(EFFECT_TYPE.equals(requiredText(effect, "type", "theme Host effect")),
                "theme Host effect.type must be " + EFFECT_TYPE);
        assertValidPayload(effect.get("payload"));
    }

    public void assertValidBootstrapResponse(JsonNode response) {
        requireObject(response, "theme bootstrap response");
        require(response.path("ok").isBoolean() && response.path("ok").asBoolean(),
                "theme bootstrap response.ok must be true");
        require(response.has("host_effect"),
                "theme bootstrap response.host_effect is required");
        require(response.has("fallback_effect"),
                "theme bootstrap response.fallback_effect is required");
        assertValidEffect(response.get("host_effect"));
        assertValidEffect(response.get("fallback_effect"));
    }

    public void assertValidPayload(JsonNode payload) {
        requireObject(payload, "theme Host payload");
        require(payload.path("schema_version").isIntegralNumber()
                        && payload.path("schema_version").asInt() == SCHEMA_VERSION,
                "theme Host payload.schema_version must be 1");
        require(PACKAGE_ID.matcher(requiredText(payload, "package_id", "theme Host payload")).matches(),
                "theme Host payload.package_id is invalid");
        require(SEMVER.matcher(requiredText(payload, "version", "theme Host payload")).matches(),
                "theme Host payload.version is invalid");
        require(INSTANCE_ID.matcher(requiredText(payload, "instance_id", "theme Host payload")).matches(),
                "theme Host payload.instance_id is invalid");
        for (String field : Set.of("tokens", "shell", "animation", "assets")) {
            requireObject(payload.get(field), "theme Host payload." + field);
        }
        requireObject(payload.path("animation").get("program"),
                "theme Host payload.animation.program");
        requireObject(payload.path("animation").get("fallback"),
                "theme Host payload.animation.fallback");
    }

    private static JsonNode requireObject(JsonNode value, String label) {
        require(value != null && value.isObject(), label + " must be an object");
        return value;
    }

    private static String requiredText(JsonNode object, String field, String label) {
        JsonNode value = object.get(field);
        require(value != null && value.isTextual() && !value.asText().isBlank(),
                label + "." + field + " must be a non-empty string");
        return value.asText();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
