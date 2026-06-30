package org.twelve.aipp.widget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads {@code aipp-themes.json} (classpath) — single source of truth shared with
 * {@code shared/theme/aipp-themes.json} and the CSS generator.
 */
public final class AippThemes {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonNode ROOT = loadRoot();

    private AippThemes() {}

    private static JsonNode loadRoot() {
        try (InputStream in = AippThemes.class.getResourceAsStream("/aipp-themes.json")) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource /aipp-themes.json");
            }
            return JSON.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse aipp-themes.json", e);
        }
    }

    /** Built-in preset names (e.g. {@code dark}, {@code light}). */
    public static java.util.Set<String> presetNames() {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        ROOT.path("presets").fieldNames().forEachRemaining(names::add);
        return names;
    }

    public static AippWidgetTheme theme(String presetName) {
        JsonNode tokens = resolveTokens(presetName);
        JsonNode preset = ROOT.path("presets").path(presetName);
        return new AippWidgetTheme(
                text(tokens, "bg"),
                text(tokens, "surface"),
                text(tokens, "surface2"),
                text(tokens, "surface3"),
                text(tokens, "text"),
                text(tokens, "textDim"),
                text(tokens, "textMuted"),
                text(tokens, "border"),
                text(tokens, "border2"),
                text(tokens, "accent"),
                text(tokens, "accentHover"),
                text(tokens, "accentGlow"),
                text(tokens, "active"),
                text(tokens, "danger"),
                text(tokens, "success"),
                text(tokens, "warning"),
                text(tokens, "info"),
                text(tokens, "font"),
                text(tokens, "fontMono"),
                tokens.path("fontSize").asInt(13),
                tokens.path("fontSizeSm").asInt(11),
                tokens.path("fontSizeLg").asInt(14),
                tokens.path("radius").asInt(8),
                tokens.path("radiusSm").asInt(5),
                tokens.path("radiusLg").asInt(10),
                tokens.path("radiusPill").asInt(999),
                preset.path("language").asText("zh"),
                preset.path("darkMode").asBoolean(true)
        );
    }

    public static Map<String, String> cssVarsForPreset(String presetName) {
        return theme(presetName).toCssVars();
    }

    private static JsonNode resolveTokens(String presetName) {
        JsonNode base = ROOT.path("tokens");
        JsonNode override = ROOT.path("presets").path(presetName);
        Map<String, Object> merged = new LinkedHashMap<>();
        base.fields().forEachRemaining(e -> merged.put(e.getKey(), e.getValue()));
        override.fields().forEachRemaining(e -> {
            String k = e.getKey();
            if ("language".equals(k) || "darkMode".equals(k)) return;
            merged.put(k, e.getValue());
        });
        return JSON.valueToTree(merged);
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }
}
