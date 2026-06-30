package org.twelve.aipp.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches effective LLM provider config from the Host ({@code GET /api/llm-config}).
 *
 * <p>See {@code spec/llm-config.md} §4.1 and §6.2.
 */
public final class HostLlmConfigClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ConcurrentHashMap<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private HostLlmConfigClient() {}

    public enum Status {
        /** Host returned {@code ok: true} with a complete config block. */
        OK,
        /** Host responded but no complete config exists ({@code ok: false}). */
        NOT_CONFIGURED,
        /** Missing/invalid transport auth when required. */
        UNAUTHORIZED,
        /** Network/parse failure contacting the Host. */
        TRANSPORT_ERROR
    }

    public record ResolvedConfig(
            String apiKey,
            String baseUrl,
            String model,
            int timeoutSeconds,
            String source) {

        public boolean hasKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record FetchResult(Status status, ResolvedConfig config, String error, String message) {}

    /** Resolve Host base URL from global config / env / fallback, then fetch. */
    public static FetchResult fetchResolving(String appFallbackHostUrl, String userId) {
        return fetch(HostUrlResolver.resolve(appFallbackHostUrl), userId, HostAccessToken.load());
    }

    public static FetchResult fetch(String hostBaseUrl, String userId) {
        return fetch(hostBaseUrl, userId, HostAccessToken.load());
    }

    public static FetchResult fetch(String hostBaseUrl, String userId, String bearerToken) {
        String host = HostUrlResolver.normalizeBaseUrl(hostBaseUrl);
        if (host.isBlank()) {
            return new FetchResult(Status.TRANSPORT_ERROR, null, "host_unconfigured", "Host base URL is blank");
        }
        String uid = userId == null ? "" : userId.trim();
        String cacheKey = host + "|" + uid;
        CacheEntry cached = CACHE.get(cacheKey);
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            return cached.result;
        }
        FetchResult result = doFetch(host, uid, bearerToken);
        if (result.status() == Status.OK) {
            CACHE.put(cacheKey, new CacheEntry(result, Instant.now().plus(CACHE_TTL)));
        }
        return result;
    }

    public static Optional<ResolvedConfig> fetchOptional(String hostBaseUrl, String userId) {
        FetchResult r = fetch(hostBaseUrl, userId);
        return r.status() == Status.OK && r.config() != null && r.config().hasKey()
                ? Optional.of(r.config())
                : Optional.empty();
    }

    /** Clears in-process cache (tests or after Host settings change). */
    public static void clearCache() {
        CACHE.clear();
    }

    private static FetchResult doFetch(String host, String userId, String bearerToken) {
        try {
            StringBuilder url = new StringBuilder(host).append("/api/llm-config");
            if (!userId.isBlank()) {
                url.append("?user_id=")
                        .append(URLEncoder.encode(userId, StandardCharsets.UTF_8));
            }
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url.toString()))
                    .timeout(DEFAULT_TIMEOUT)
                    .GET();
            if (bearerToken != null && !bearerToken.isBlank()) {
                req.header("Authorization", "Bearer " + bearerToken.trim());
            }
            HttpResponse<String> resp = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 401) {
                return new FetchResult(Status.UNAUTHORIZED, null, "unauthorized", "Host rejected transport auth");
            }
            if (resp.statusCode() >= 500 || resp.body() == null || resp.body().isBlank()) {
                return new FetchResult(Status.TRANSPORT_ERROR, null, "host_error",
                        "Host LLM config request failed with HTTP " + resp.statusCode());
            }
            JsonNode root = JSON.readTree(resp.body());
            if (!root.path("ok").asBoolean(false)) {
                return new FetchResult(
                        Status.NOT_CONFIGURED,
                        null,
                        root.path("error").asText("llm_not_configured"),
                        root.path("message").asText(""));
            }
            JsonNode config = root.get("config");
            if (config == null || !config.isObject()) {
                return new FetchResult(Status.NOT_CONFIGURED, null, "llm_not_configured",
                        "Host response missing config block");
            }
            String apiKey = firstNonBlank(
                    config.path("api_key").asText(""),
                    config.path("apiKey").asText(""));
            String baseUrl = firstNonBlank(
                    config.path("base_url").asText(""),
                    config.path("baseUrl").asText(""));
            String model = config.path("model").asText("").trim();
            int timeout = config.path("timeout_seconds").asInt(0);
            if (timeout <= 0) timeout = config.path("timeoutSeconds").asInt(120);
            if (apiKey.isBlank() || baseUrl.isBlank() || model.isBlank()) {
                return new FetchResult(Status.NOT_CONFIGURED, null, "llm_not_configured",
                        "Host config block incomplete");
            }
            ResolvedConfig resolved = new ResolvedConfig(
                    apiKey, baseUrl, model, timeout, root.path("source").asText(""));
            return new FetchResult(Status.OK, resolved, null, null);
        } catch (Exception e) {
            return new FetchResult(Status.TRANSPORT_ERROR, null, "transport_error",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        return b == null ? "" : b.trim();
    }

    private record CacheEntry(FetchResult result, Instant expiresAt) {}
}
