package org.twelve.shared.trace;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fire-and-forget trace ingest to world-one {@code POST /api/trace/events}.
 * AIPPs depend on trace-client and use this; the host uses {@code TraceService} in-process.
 */
public final class TraceHttpEmitter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String ingestUrl;
    private final HttpClient http;

    public TraceHttpEmitter(String hostBaseUrl) {
        this(hostBaseUrl, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    public TraceHttpEmitter(String hostBaseUrl, HttpClient http) {
        String base = hostBaseUrl == null ? "" : hostBaseUrl.trim().replaceAll("/+$", "");
        this.ingestUrl = base + "/api/trace/events";
        this.http = http;
    }

    /** Best-effort POST; failures are swallowed (trace must not break workflows). */
    public void emit(TraceEvent event) {
        if (event == null) return;
        emitBatch(List.of(event));
    }

    public void emitBatch(List<TraceEvent> events) {
        if (events == null || events.isEmpty()) return;
        try {
            List<Map<String, Object>> payload = events.stream().map(TraceEvent::toMap).toList();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("events", payload);
            byte[] bytes = JSON.writeValueAsBytes(body);
            HttpRequest req = HttpRequest.newBuilder(URI.create(ingestUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                    .build();
            http.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // operational trace is diagnostic only
        }
    }
}
