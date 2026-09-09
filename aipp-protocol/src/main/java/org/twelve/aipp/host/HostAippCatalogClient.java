package org.twelve.aipp.host;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Read-only client for the provider-neutral Host AIPP aggregate. */
public final class HostAippCatalogClient {
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final String hostBaseUrl;
    private final Duration timeout;

    public HostAippCatalogClient(String hostBaseUrl) {
        this(hostBaseUrl, Duration.ofSeconds(10));
    }

    public HostAippCatalogClient(String hostBaseUrl, Duration timeout) {
        this.hostBaseUrl = HostUrlResolver.normalizeBaseUrl(hostBaseUrl);
        this.timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        this.http = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    public static HostAippCatalogClient resolving(String fallbackHostUrl) {
        return new HostAippCatalogClient(HostUrlResolver.resolve(fallbackHostUrl));
    }

    public Map<String, Object> snapshot() {
        if (hostBaseUrl.isBlank()) return Map.of();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(hostBaseUrl + HostAippCatalogSpec.PATH))
                    .timeout(timeout).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300
                    || response.body() == null || response.body().isBlank()) return Map.of();
            return json.readValue(response.body(), new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}
