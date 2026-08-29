package org.twelve.llmgateway.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.twelve.llmgateway.contract.*;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Internal Gateway transport. It can only forward opaque credentials; it has no signing API. */
public final class LlmGatewayClient implements GatewayClient {
    public static final String SERVICE_HEADER = GatewayProtocol.SERVICE_HEADER;
    public static final String SERVICE_CREDENTIAL_HEADER = GatewayProtocol.SERVICE_CREDENTIAL_HEADER;
    public static final String DELEGATION_HEADER = GatewayProtocol.DELEGATION_HEADER;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).version(HttpClient.Version.HTTP_1_1).build();
    private static final ExecutorService STREAMS = Executors.newVirtualThreadPerTaskExecutor();

    private final URI baseUri;
    private final String serviceIdentity;
    private final String serviceCredential;
    private final GatewayCredentialProvider credentials;
    private final ObjectMapper json;

    public LlmGatewayClient(URI baseUri, String serviceIdentity, String serviceCredential,
                            GatewayCredentialProvider credentials) {
        this(baseUri, serviceIdentity, serviceCredential, credentials,
                new ObjectMapper().findAndRegisterModules());
    }

    LlmGatewayClient(URI baseUri, String serviceIdentity, String serviceCredential,
                     GatewayCredentialProvider credentials, ObjectMapper json) {
        this.baseUri = Objects.requireNonNull(baseUri);
        this.serviceIdentity = required(serviceIdentity, "serviceIdentity");
        this.serviceCredential = required(serviceCredential, "serviceCredential");
        this.credentials = Objects.requireNonNull(credentials);
        this.json = Objects.requireNonNull(json);
    }

    @Override public ChatInvocationResponse chat(ChatInvocationRequest request,
                                                  GatewayRequestMetadata metadata) throws Exception {
        return send("/internal/llm/chat/invocations", request, metadata, GatewayOperation.CHAT,
                ChatInvocationResponse.class);
    }

    @Override public EmbeddingInvocationResponse embedding(EmbeddingInvocationRequest request,
                                                            GatewayRequestMetadata metadata) throws Exception {
        return send("/internal/llm/embedding/invocations", request, metadata, GatewayOperation.EMBEDDING,
                EmbeddingInvocationResponse.class);
    }

    @Override public StreamHandle streamChat(ChatInvocationRequest request, GatewayRequestMetadata metadata,
                                             Consumer<GatewayStreamEvent> events) throws Exception {
        HttpRequest httpRequest = authorized("/internal/llm/chat/invocations", metadata, GatewayOperation.CHAT)
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(payload(request, metadata)))).build();
        HttpResponse<InputStream> response = HTTP.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) {
            try (InputStream input = response.body()) { throw error(response.statusCode(), input.readNBytes(4096)); }
        }
        InputStream input = response.body();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        GatewayStreamEvent first = readEvent(reader);
        if (first == null || !"invocation".equals(first.type()) || first.invocationId() == null) {
            input.close(); throw new IllegalStateException("Gateway SSE did not start with invocation_id");
        }
        events.accept(first);
        CompletableFuture<ChatInvocationResponse> completion = new CompletableFuture<>();
        STREAMS.submit(() -> {
            GatewayUsage usage = null;
            try (input; reader) {
                GatewayStreamEvent event;
                while ((event = readEvent(reader)) != null) {
                    events.accept(event); if (event.usage() != null) usage = event.usage();
                }
                completion.complete(new ChatInvocationResponse(first.invocationId(), "stop", null,
                        null, List.of(), Map.of(), usage));
            } catch (Throwable failure) { completion.completeExceptionally(failure); }
        });
        return new StreamHandle(first.invocationId(), completion, () -> {
            try { input.close(); } catch (Exception ignored) {}
        });
    }

    private <T> T send(String path, Object body, GatewayRequestMetadata metadata,
                       GatewayOperation operation, Class<T> responseType) throws Exception {
        HttpRequest request = authorized(path, metadata, operation)
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(payload(body, metadata)))).build();
        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() / 100 != 2) throw error(response.statusCode(), response.body());
        return json.readValue(response.body(), responseType);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(Object body, GatewayRequestMetadata metadata) {
        Map<String, Object> payload = new LinkedHashMap<>(json.convertValue(body, Map.class));
        payload.put("session_id", metadata.sessionId()); payload.put("turn_id", metadata.turnId());
        payload.put("feature_code", metadata.featureCode()); payload.put("call_type", metadata.callType());
        payload.put("job_id", metadata.jobId());
        return payload;
    }

    private GatewayStreamEvent readEvent(BufferedReader reader) throws Exception {
        String line;
        while ((line = reader.readLine()) != null) if (line.startsWith("data:")) {
            String data = line.substring(5).strip();
            if (!data.isEmpty()) return json.readValue(data, GatewayStreamEvent.class);
        }
        return null;
    }

    private HttpRequest.Builder authorized(String path, GatewayRequestMetadata metadata,
                                           GatewayOperation operation) {
        String delegation = required(credentials.credentialFor(operation, metadata), "delegationCredential");
        return HttpRequest.newBuilder(baseUri.resolve(path)).timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                .header(SERVICE_HEADER, serviceIdentity)
                .header(SERVICE_CREDENTIAL_HEADER, serviceCredential)
                .header(DELEGATION_HEADER, delegation);
    }

    private GatewayHttpException error(int status, byte[] body) {
        String raw = new String(body, StandardCharsets.UTF_8);
        try {
            GatewayError envelope = json.readValue(body, GatewayError.class);
            return new GatewayHttpException(status, envelope.code(), envelope.message(), envelope.retryable());
        } catch (Exception ignored) {
            return new GatewayHttpException(status, GatewayErrorCode.INTERNAL_ERROR, raw, status >= 500);
        }
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is not configured");
        return value.strip();
    }
    public static final class GatewayHttpException extends RuntimeException {
        private final int status; private final GatewayErrorCode code; private final boolean retryable;
        GatewayHttpException(int status, GatewayErrorCode code, String message, boolean retryable) {
            super(message); this.status = status; this.code = code; this.retryable = retryable;
        }
        public int status() { return status; } public GatewayErrorCode code() { return code; }
        public boolean retryable() { return retryable; }
    }
}
