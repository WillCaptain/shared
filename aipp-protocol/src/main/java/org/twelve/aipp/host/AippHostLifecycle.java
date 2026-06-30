package org.twelve.aipp.host;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Standard AIPP ↔ Host lifecycle: an AIPP registers itself with the Host (world-one) on launch
 * and deregisters on shutdown, so it appears in / disappears from the Host app list automatically.
 *
 * <p>Framework-agnostic (pure {@code java.net.http}); wire it from your app's startup hook
 * (e.g. Spring {@code ApplicationReadyEvent}). Prefer {@link #startAttachLoop()}: the AIPP keeps
 * (re)attaching to the Host on a daemon thread, so a living AIPP always returns to the Host app
 * list after a Host restart or a stale eviction — the Host never has to discover AIPPs. The Host
 * treats a same-instance refresh as an idempotent ack and additionally probes each app in the
 * background, so liveness needs no extra client action — just keep the core endpoints fast.
 *
 * <p>Deregister-on-shutdown is <b>optional</b> and discouraged for long-lived AIPPs: a partial
 * shutdown can evict a replacement instance. Presence is healed by the attach loop + the Host
 * liveness probe instead.
 *
 * <p>Contract (see aipp-protocol {@code spec/host-lifecycle.md}):
 * <ul>
 *   <li>Attach/refresh: {@code POST {host}/api/registry/install} {@code {app_id, base_url, instance_id}}</li>
 *   <li>Deregister (optional): {@code DELETE {host}/api/registry/{app_id}?instance_id=…}</li>
 * </ul>
 *
 * <p>Spring Boot apps: use {@code aipp-protocol-spring} ({@code AippHostAttachAutoConfiguration})
 * instead of wiring this class manually.
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
    /** Per-JVM id so a stale shutdown cannot deregister a replacement instance. */
    private final String instanceId;

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
        this(hostBaseUrl, appId, selfBaseUrl, UUID.randomUUID().toString(), http);
    }

    public AippHostLifecycle(String hostBaseUrl, String appId, String selfBaseUrl, String instanceId,
                             HttpClient http) {
        this.hostBaseUrl = trimTrailingSlash(hostBaseUrl);
        this.appId = appId == null ? "" : appId.trim();
        this.selfBaseUrl = trimTrailingSlash(selfBaseUrl);
        this.instanceId = instanceId == null || instanceId.isBlank()
                ? UUID.randomUUID().toString() : instanceId.trim();
        this.http = http;
    }

    public boolean configured() {
        return !hostBaseUrl.isBlank() && !appId.isBlank() && !selfBaseUrl.isBlank();
    }

    public String appId() { return appId; }
    public String hostBaseUrl() { return hostBaseUrl; }
    public String selfBaseUrl() { return selfBaseUrl; }
    public String instanceId() { return instanceId; }

    /** One-shot registration with the Host. */
    public Result register() {
        if (!configured()) {
            return Result.fail(0, null, "host lifecycle not configured (host/app_id/base_url)");
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("app_id", appId);
            payload.put("base_url", selfBaseUrl);
            payload.put("instance_id", instanceId);
            String body = JSON.writeValueAsString(payload);
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

    /** Background attach loop state. */
    private volatile Thread attachThread;
    private volatile boolean attached;

    /** Default cadence for the persistent attach loop. */
    public static final int DEFAULT_INITIAL_ATTEMPTS = 30;
    public static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(5);
    public static final Duration DEFAULT_REFRESH_INTERVAL = Duration.ofSeconds(15);

    /** @return {@code true} once the most recent attach/refresh succeeded. */
    public boolean attached() { return attached; }

    /**
     * Start a persistent <b>attach loop</b> on a daemon thread (standard AIPP behavior):
     * register with retry while the Host is unreachable, then keep refreshing on
     * {@code refreshInterval}. The Host treats a same-instance refresh as an idempotent ack,
     * so this is cheap and heals the app list after a Host restart or a stale eviction.
     *
     * <p>Idempotent: calling it again stops any previous loop first.
     */
    public synchronized void startAttachLoop(int maxInitialAttempts, Duration retryDelay,
                                             Duration refreshInterval) {
        if (!configured()) return;
        stop();
        final long refreshMs = Math.max(1000L, refreshInterval.toMillis());
        Thread t = new Thread(() -> {
            attached = registerWithRetry(maxInitialAttempts, retryDelay).success();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(refreshMs);
                    attached = register().success();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }, appId + "-host-attach");
        t.setDaemon(true);
        attachThread = t;
        t.start();
    }

    /** Start the attach loop with the default cadence. */
    public void startAttachLoop() {
        startAttachLoop(DEFAULT_INITIAL_ATTEMPTS, DEFAULT_RETRY_DELAY, DEFAULT_REFRESH_INTERVAL);
    }

    /** Stop the attach loop (does not deregister). Safe to call multiple times. */
    public synchronized void stop() {
        Thread t = attachThread;
        attachThread = null;
        if (t != null) t.interrupt();
    }

    /** Deregister from the Host (idempotent on the Host side). */
    public Result deregister() {
        if (!configured()) {
            return Result.fail(0, null, "host lifecycle not configured (host/app_id/base_url)");
        }
        try {
            String url = hostBaseUrl + "/api/registry/" + appId
                    + "?instance_id=" + java.net.URLEncoder.encode(instanceId, java.nio.charset.StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
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
