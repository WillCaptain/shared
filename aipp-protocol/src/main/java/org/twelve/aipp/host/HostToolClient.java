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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Host-brokered tool client: calls a named capability tool through world-one's
 * tool proxy ({@code POST {host}/api/proxy/tools/{toolName}} with body
 * {@code {"args": {...}}}). Consumers depend on tool <em>names</em>, never on a
 * specific provider app id or base URL — the Host routes by name
 * ({@code AppRegistry.findAppForTool}).
 *
 * <p>This is the single shared transport reused by every AIPP that consumes a
 * capability (e.g. note-one and decision-reactor consuming the ontology-world
 * capability). It is a plain JDK class (no Spring) so it can live in
 * {@code aipp-protocol}; apps wire it as a bean. See
 * {@code spec/ontology-world-capability.md} and {@code spec/capability-providers.md}.
 */
public final class HostToolClient {

    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final String hostBaseUrl;
    private final Duration timeout;
    private final String callerAppId;
    private final String callbackToken;

    public HostToolClient(String hostBaseUrl) {
        this(hostBaseUrl, Duration.ofSeconds(30));
    }

    public HostToolClient(String hostBaseUrl, Duration timeout) {
        this(hostBaseUrl, timeout, "", "");
    }

    /**
     * @param callerAppId app that declares {@code requires} for the tools it calls back
     * @param callbackToken shared {@code AIPP_HOST_CALLBACK_TOKEN}; blank disables the callback credential
     */
    public HostToolClient(String hostBaseUrl, Duration timeout, String callerAppId, String callbackToken) {
        this.hostBaseUrl = HostUrlResolver.normalizeBaseUrl(hostBaseUrl);
        this.timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        this.callerAppId = callerAppId == null ? "" : callerAppId.trim();
        this.callbackToken = callbackToken == null ? "" : callbackToken.trim();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** Resolve the Host base URL from {@code ~/.ones/host.json}/env/fallback, then build a client. */
    public static HostToolClient resolving(String appFallbackHostUrl) {
        return new HostToolClient(HostUrlResolver.resolve(appFallbackHostUrl));
    }

    /**
     * Same as {@link #resolving(String)}, with the app id and shared callback token
     * the Host accepts for tools this app declares in {@code requires}.
     */
    public static HostToolClient resolving(String appFallbackHostUrl, String callerAppId, String callbackToken) {
        return new HostToolClient(HostUrlResolver.resolve(appFallbackHostUrl), Duration.ofSeconds(30),
                callerAppId, callbackToken);
    }

    public String hostBaseUrl() {
        return hostBaseUrl;
    }

    /**
     * Invoke a named tool through the Host proxy.
     *
     * @param toolName the capability tool name (e.g. {@code wiki_ensure})
     * @param args     the tool arguments (wrapped as {@code {"args": args}})
     * @return the parsed JSON response body, or {@code null} on transport/5xx failure
     */
    public JsonNode call(String toolName, Map<String, Object> args) {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName is required");
        }
        if (hostBaseUrl.isBlank()) {
            throw new IllegalStateException("host base url is not configured");
        }
        String url = hostBaseUrl + "/api/proxy/tools/"
                + URLEncoder.encode(toolName, StandardCharsets.UTF_8);
        Map<String, Object> body = requestBody(args);
        try {
            String payload = json.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", "application/json");
            applyCallback(builder, body.get("_context"));
            HttpRequest req = builder.POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 500) return null;
            String respBody = resp.body();
            if (respBody == null || respBody.isBlank()) return null;
            return json.readTree(respBody);
        } catch (Exception e) {
            return null;
        }
    }

    /** {@code _context} is the caller identity, beside {@code args}, where the Host reads it. */
    static Map<String, Object> requestBody(Map<String, Object> args) {
        Map<String, Object> source = args == null ? Map.of() : new LinkedHashMap<>(args);
        Object context = source.remove("_context");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("args", source);
        if (context instanceof Map<?, ?> ctx && !ctx.isEmpty()) {
            body.put("_context", ctx);
        }
        return body;
    }

    private void applyCallback(HttpRequest.Builder builder, Object context) {
        if (callerAppId.isBlank() || callbackToken.isBlank() || !(context instanceof Map<?, ?> ctx)) return;
        Object user = ctx.get("userId");
        if (user == null) user = ctx.get("user_id");
        String userId = user == null ? "" : String.valueOf(user).trim();
        if (!userId.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) return;
        builder.header("Authorization", "Bearer " + callbackToken);
        builder.header("X-Aipp-App-Id", callerAppId);
        builder.header("X-AIPP-User-Id", userId);
    }
}
