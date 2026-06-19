package org.twelve.aipp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads the canonical {@code GET /api/app} manifest from a classpath JSON resource.
 *
 * <p>Each AIPP owns its display metadata in {@code aipp-app.json} (or
 * {@code aipp-app-{app_id}.json} for Host builtins without a dedicated HTTP service).
 * Host code must not hardcode {@code app_name} / {@code app_icon} — read via
 * {@code GET /api/app} or this loader at startup.
 */
public final class AippAppManifestLoader {

    private static final ObjectMapper JSON = new ObjectMapper();

    private AippAppManifestLoader() {}

    /** Default resource path: {@code /aipp-app.json} on the classpath. */
    public static Map<String, Object> loadDefault() {
        return loadClasspath("/aipp-app.json");
    }

    /** Load {@code /aipp-app-{appId}.json} (leading slash optional). */
    public static Map<String, Object> loadForApp(String appId) {
        if (appId == null || appId.isBlank()) return Map.of();
        String path = "/aipp-app-" + appId.trim() + ".json";
        Map<String, Object> m = loadClasspath(path);
        return m.isEmpty() ? loadDefault() : m;
    }

    public static Map<String, Object> loadClasspath(String resourcePath) {
        String p = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
        try (InputStream in = AippAppManifestLoader.class.getResourceAsStream(p)) {
            if (in == null) return Map.of();
            Map<String, Object> raw = JSON.readValue(in, new TypeReference<>() {});
            return new LinkedHashMap<>(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load AIPP app manifest from classpath: " + p, e);
        }
    }

    /** Returns a copy with {@code configuration} merged in (runtime block from code). */
    public static Map<String, Object> withConfiguration(Map<String, Object> base,
                                                        Map<String, Object> configuration) {
        Map<String, Object> out = new LinkedHashMap<>(base);
        if (configuration != null && !configuration.isEmpty()) {
            out.put("configuration", configuration);
        }
        return out;
    }
}
