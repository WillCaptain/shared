package org.twelve.aipp.host;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Standard AIPP ↔ Host lifecycle: an AIPP registers itself with the Host (world-one) on launch
 * and deregisters on shutdown, so it appears in / disappears from the Host app list automatically.
 *
 * <p>Framework-agnostic (pure {@code java.net.http}); wire it from your app's startup/shutdown
 * hooks (e.g. Spring {@code ApplicationReadyEvent} + {@code @PreDestroy}). The Host probes each
 * app's {@code GET /api/tools} when the app list is opened, so liveness needs no client action —
 * just keep the core endpoints fast.
 *
 * <p>Contract (see aipp-protocol {@code spec/host-lifecycle.md}):
 * <ul>
 *   <li>Register: {@code POST {host}/api/registry/install} {@code {app_id, base_url}}</li>
 *   <li>Deregister: {@code DELETE {host}/api/registry/{app_id}}</li>
 * </ul>
 */
public final class AippHostLifecycle {

    /** Result of a register/deregister attempt. */
    public record Result(boolean success, int statusCode, String body, String error) {
        public static Result ok(int status, String body) { return new Result(true, status, body, null); }
        public static Result fail(int status, String body, String error) { return new Result(false, status, body, error); }
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http;
    private final String hostBaseUrl;
    private final String appId;
    private final String selfBaseUrl;

    /**
     * @param hostBaseUrl Host (world-one) base URL, e.g. {@code http://127.0.0.1:8090}
     * @param appId       this app's kebab-case id (must match {@code /api/app.app_id})
     * @param selfBaseUrl the externally reachable base URL the Host should use to reach this app
     */
    public AippHostLifecycle(String hostBaseUrl, String appId, String selfBaseUrl) {
        this(hostBaseUrl, appId, selfBaseUrl,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    public AippHostLifecycle(String hostBaseUrl, String appId, String selfBaseUrl, HttpClient http) {
        this.hostBaseUrl = trimTrailingSlash(hostBaseUrl);
        this.appId = appId == null ? "" : appId.trim();
        this.selfBaseUrl = trimTrailingSlash(selfBaseUrl);
        this.http = http;
    }

    public boolean configured() {
        return !hostBaseUrl.isBlank() && !appId.isBlank() && !selfBaseUrl.isBlank();
    }

    public String appId() { return appId; }
    public String hostBaseUrl() { return hostBaseUrl; }
    public String selfBaseUrl() { return selfBaseUrl; }

    /** One-shot registration with the Host. */
    public Result register() {
        if (!configured()) {
            return Result.fail(0, null, "host lifecycle not configured (host/app_id/base_url)");
        }
        try {
            String body = JSON.writeValueAsString(Map.of("app_id", appId, "base_url", selfBaseUrl));
            HttpResponse<String> resp = post(hostBaseUrl + "/api/registry/install", body);
            boolean ok = resp.statusCode() / 100 == 2
                    && resp.body() != null && resp.body().contains("\"success\":true");
            return ok ? Result.ok(resp.statusCode(), resp.body())
                      : Result.fail(resp.statusCode(), resp.body(), "install not successful");
        } catch (Exception e) {
            return Result.fail(0, null, e.getMessage());
        }
    }

    /**
     * Register, retrying while the Host is unreachable (it may start after this AIPP).
     * Blocks the calling thread; run it on a daemon thread for non-blocking startup.
     *
     * @return the successful {@link Result}, or the last failure after {@code maxAttempts}
     */
    public Result registerWithRetry(int maxAttempts, Duration retryDelay) {
        Result last = Result.fail(0, null, "no attempt");
        for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
            last = register();
            if (last.success()) return last;
            try {
                Thread.sleep(Math.max(0, retryDelay.toMillis()));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return last;
            }
        }
        return last;
    }

    /** Deregister from the Host (idempotent on the Host side). */
    public Result deregister() {
        if (!configured()) {
            return Result.fail(0, null, "host lifecycle not configured (host/app_id/base_url)");
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(hostBaseUrl + "/api/registry/" + appId))
                    .timeout(Duration.ofSeconds(10))
                    .DELETE()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            boolean ok = resp.statusCode() / 100 == 2;
            return ok ? Result.ok(resp.statusCode(), resp.body())
                      : Result.fail(resp.statusCode(), resp.body(), "deregister not successful");
        } catch (Exception e) {
            return Result.fail(0, null, e.getMessage());
        }
    }

    private HttpResponse<String> post(String url, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) return "";
        String t = s.trim();
        while (t.endsWith("/")) t = t.substring(0, t.length() - 1);
        return t;
    }
}
