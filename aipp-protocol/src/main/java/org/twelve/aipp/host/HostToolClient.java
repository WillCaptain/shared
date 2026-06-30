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

    public HostToolClient(String hostBaseUrl) {
        this(hostBaseUrl, Duration.ofSeconds(30));
    }

    public HostToolClient(String hostBaseUrl, Duration timeout) {
        this.hostBaseUrl = HostUrlResolver.normalizeBaseUrl(hostBaseUrl);
        this.timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** Resolve the Host base URL from {@code ~/.ones/host.json}/env/fallback, then build a client. */
    public static HostToolClient resolving(String appFallbackHostUrl) {
        return new HostToolClient(HostUrlResolver.resolve(appFallbackHostUrl));
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
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("args", args == null ? Map.of() : args);
        try {
            String payload = json.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 500) return null;
            String respBody = resp.body();
            if (respBody == null || respBody.isBlank()) return null;
            return json.readTree(respBody);
        } catch (Exception e) {
            return null;
        }
    }
}
