package org.twelve.aipp.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves world-one Host base URL for standalone AIPP processes.
 *
 * <p>Host URL is deployment config ({@code ~/.ones/host.json}, env, app default),
 * not AIPP {@code configuration} widget values. Host {@code PUT /api/host/bindings}
 * injects {@code env} only; URL fields are derived locally.
 */
public final class HostUrlResolver {

    public static final String DEFAULT_HOST_BASE_URL = "http://127.0.0.1:8090";
    private static final ObjectMapper JSON = new ObjectMapper();

    private HostUrlResolver() {}

    public static Path globalConfigPath() {
        String override = firstNonBlank(
                System.getenv("AIPP_HOST_CONFIG"),
                System.getProperty("aipp.host.config"));
        if (!override.isBlank()) {
            return Path.of(override.trim());
        }
        return Path.of(System.getProperty("user.home"), ".ones", "host.json");
    }

    /** Resolve Host base URL without injected bindings. */
    public static String resolve(String appFallback) {
        String fromFile = loadFromGlobalConfig();
        if (!fromFile.isBlank()) return fromFile;

        String fromEnv = firstNonBlank(
                System.getenv("AIPP_HOST_BASE_URL"),
                System.getenv("WORLD_ONE_BASE_URL"));
        if (!fromEnv.isBlank()) return normalizeBaseUrl(fromEnv);

        if (appFallback != null && !appFallback.isBlank()) {
            return normalizeBaseUrl(appFallback);
        }
        return DEFAULT_HOST_BASE_URL;
    }

    /**
     * Prefer legacy injected {@code host_base_url} when present (backward compat),
     * otherwise {@link #resolve(String)}.
     */
    public static String resolveFromStore(HostBindingsStore store, String appFallback) {
        if (store != null) {
            String injected = store.getString("host_base_url", "");
            if (!injected.isBlank()) return normalizeBaseUrl(injected);
        }
        return resolve(appFallback);
    }

    /** {@code {host_base_url}/api/host/event-callbacks/{app_id}} */
    public static String eventCallbackBaseUrl(HostBindingsStore store, String appId, String appFallback) {
        String base = resolveFromStore(store, appFallback);
        if (base.isBlank() || appId == null || appId.isBlank()) return "";
        return base + "/api/host/event-callbacks/" + appId.trim();
    }

    static String loadFromGlobalConfig() {
        Path path = globalConfigPath();
        if (!Files.isRegularFile(path)) return "";
        try {
            JsonNode root = JSON.readTree(path.toFile());
            String raw = root.path("host_base_url").asText("").trim();
            if (raw.isBlank()) return "";
            return normalizeBaseUrl(raw);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static String normalizeBaseUrl(String base) {
        if (base == null || base.isBlank()) return "";
        String s = base.trim();
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "http://" + s;
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.trim().isBlank()) return v.trim();
        }
        return "";
    }
}
