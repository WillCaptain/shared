package org.twelve.shared.trace;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Best-effort, non-blocking transport to the World-One trace ingest endpoint. */
public final class TraceHttpEmitter {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final URI endpoint;
    private final HttpClient client;
    private final ObjectMapper mapper;

    public TraceHttpEmitter(String hostBaseUrl) {
        this(hostBaseUrl, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    }

    /** Compatibility constructor retained for existing shared-module consumers. */
    public TraceHttpEmitter(String hostBaseUrl, HttpClient client) {
        this(endpoint(hostBaseUrl), client, new ObjectMapper());
    }

    TraceHttpEmitter(URI endpoint, HttpClient client, ObjectMapper mapper) {
        this.endpoint = endpoint;
        this.client = client;
        this.mapper = mapper;
    }

    public void emit(TraceEvent event) {
        emitAsync(event);
    }

    public void emitBatch(List<TraceEvent> events) {
        emitBatchAsync(events);
    }

    public CompletableFuture<Void> emitAsync(TraceEvent event) {
        return event == null ? CompletableFuture.completedFuture(null) : emitBatchAsync(List.of(event));
    }

    public CompletableFuture<Void> emitBatchAsync(List<TraceEvent> events) {
        if (events == null || events.isEmpty()) return CompletableFuture.completedFuture(null);
        try {
            List<Map<String, Object>> payload = events.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(TraceEvent::toMap)
                    .toList();
            if (payload.isEmpty()) return CompletableFuture.completedFuture(null);
            byte[] body = mapper.writeValueAsBytes(Map.of("events", payload));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenApply(ignored -> (Void) null)
                    .exceptionally(ignored -> null);
        } catch (RuntimeException | java.io.IOException ignored) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static URI endpoint(String hostBaseUrl) {
        if (hostBaseUrl == null || hostBaseUrl.isBlank()) {
            throw new IllegalArgumentException("hostBaseUrl is required");
        }
        String base = hostBaseUrl.trim().replaceAll("/+$", "");
        if (base.endsWith("/api/trace/events")) return URI.create(base);
        return URI.create(base + "/api/trace/events");
    }
}
