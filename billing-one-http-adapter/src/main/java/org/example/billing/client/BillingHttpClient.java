package org.example.billing.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.example.billing.contract.*;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Internal Billing-One transport. It forwards opaque assertions and service credentials, but
 * deliberately has no token signer, pricing calculation, account access or Ledger API.
 */
public final class BillingHttpClient implements BillingClientPort {
    public static final String DECISIONS_PATH = "/internal/billing/decisions";
    public static final String USAGE_EVENTS_PATH = "/internal/billing/usage-events";

    private final BillingClientConfiguration configuration;
    private final HttpClient http;
    private final ObjectMapper json;
    private final MeterRegistry metrics;
    private final int maxAttempts;

    public BillingHttpClient(BillingClientConfiguration configuration) {
        this(configuration, HttpClient.newBuilder().connectTimeout(configuration.connectTimeout()).build(),
                JsonMapper.builder().findAndAddModules()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build(), null, 2);
    }

    /** Metrics are optional so the transport remains usable by non-Spring contract tests. */
    public BillingHttpClient(BillingClientConfiguration configuration, MeterRegistry metrics) {
        this(configuration, HttpClient.newBuilder().connectTimeout(configuration.connectTimeout()).build(),
                JsonMapper.builder().findAndAddModules()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build(), metrics, 2);
    }

    BillingHttpClient(BillingClientConfiguration configuration, HttpClient http, ObjectMapper json) {
        this(configuration, http, json, null, 2);
    }

    BillingHttpClient(BillingClientConfiguration configuration, HttpClient http, ObjectMapper json,
                      MeterRegistry metrics, int maxAttempts) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.http = Objects.requireNonNull(http, "http");
        this.json = Objects.requireNonNull(json, "json");
        this.metrics = metrics;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Override
    public BillingDecisionResponse decide(BillingDecisionRequest request, BillingRequestMetadata metadata) {
        Objects.requireNonNull(request, "request");
        return send(DECISIONS_PATH, request, metadata, request.idempotencyKey(), BillingDecisionResponse.class);
    }

    @Override
    public void publish(BillingUsageEvent event, BillingRequestMetadata metadata) {
        Objects.requireNonNull(event, "event");
        send(USAGE_EVENTS_PATH, event, metadata, event.eventId(), Void.class);
    }

    public void grant(GrantCommand command, BillingRequestMetadata metadata) {
        send("/internal/billing/grants", command, metadata, command.idempotencyKey(), Void.class);
    }

    public void revoke(RevokeCommand command, BillingRequestMetadata metadata) {
        send("/internal/billing/revocations", command, metadata, command.idempotencyKey(), Void.class);
    }

    public void adjust(AdjustmentCommand command, BillingRequestMetadata metadata) {
        send("/internal/billing/adjustments", command, metadata, command.idempotencyKey(), Void.class);
    }

    @Override
    public BillingUsageSettlement settlement(BillingRequestMetadata metadata, String eventId) {
        return get("/internal/billing/usage-events/" + encode(eventId), metadata,
                BillingUsageSettlement.class, "settlement");
    }

    @Override
    public BillingBalance balance(BillingRequestMetadata metadata, String subjectUserId) {
        return get("/api/credits/balance?user_id=" + encode(subjectUserId), metadata,
                BillingBalance.class, "balance");
    }

    @Override
    public java.util.List<BillingTransaction> transactions(BillingRequestMetadata metadata, String subjectUserId,
                                                            int limit) {
        TransactionBody body = get("/api/credits/transactions?user_id=" + encode(subjectUserId)
                + "&limit=" + Math.max(1, Math.min(limit, 200)), metadata, TransactionBody.class, "transactions");
        return body == null || body.transactions == null ? java.util.List.of() : body.transactions;
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> expiring(BillingRequestMetadata metadata,
                                                                    String subjectUserId, int days) {
        ExpiringBody body = get("/api/credits/expiring?user_id=" + encode(subjectUserId)
                + "&days=" + Math.max(0, days), metadata, ExpiringBody.class, "expiring");
        return body == null || body.lots == null ? java.util.List.of() : body.lots;
    }

    private <T> T send(String path, Object body, BillingRequestMetadata metadata, String idempotencyKey,
                       Class<T> responseType) {
        Objects.requireNonNull(metadata, "metadata");
        String operation = operation(path);
        try {
            String payload = json.writeValueAsString(body);
            return execute(operation, () -> {
                HttpRequest.Builder builder = authenticatedRequest(path, metadata)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("Idempotency-Key", idempotencyKey);
                return http.send(builder.POST(HttpRequest.BodyPublishers.ofString(payload)).build(),
                        HttpResponse.BodyHandlers.ofString());
            }, response -> {
                if (responseType == Void.class || response.body() == null || response.body().isBlank()) return null;
                return json.readValue(response.body(), responseType);
            });
        } catch (IOException | RuntimeException e) {
            if (e instanceof BillingClientException typed) throw typed;
            throw transportFailure(operation, e);
        }
    }

    private <T> T get(String path, BillingRequestMetadata metadata, Class<T> responseType, String operation) {
        Objects.requireNonNull(metadata, "metadata");
        try {
            return execute(operation, () -> {
                HttpRequest.Builder builder = authenticatedRequest(path, metadata)
                        .header("Accept", "application/json");
                return http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
            }, response -> json.readValue(response.body(), responseType));
        } catch (IOException | RuntimeException e) {
            if (e instanceof BillingClientException typed) throw typed;
            throw transportFailure(operation, e);
        }
    }

    private <T> T execute(String operation, HttpCall call, ResponseParser<T> parser)
            throws IOException {
        BillingClientException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long started = System.nanoTime();
            try {
                HttpResponse<String> response = call.send();
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    BillingClientException rejected = error(response.statusCode(), response.body());
                    recordHttp(operation, result(rejected), started);
                    if (!rejected.retryable() || attempt == maxAttempts) throw rejected;
                    last = rejected;
                    retry(operation, attempt);
                    continue;
                }
                try {
                    T parsed = parser.parse(response);
                    recordHttp(operation, "success", started);
                    return parsed;
                } catch (IOException | RuntimeException parseFailure) {
                    recordHttp(operation, "failure", started);
                    if (attempt == maxAttempts) throw parseFailure;
                    last = transportFailure(operation, parseFailure);
                    retry(operation, attempt);
                }
            } catch (java.net.http.HttpTimeoutException timeout) {
                recordHttp(operation, "timeout", started);
                if (attempt == maxAttempts) throw transportFailure(operation, timeout);
                last = transportFailure(operation, timeout);
                retry(operation, attempt);
            } catch (ConnectException unavailable) {
                recordHttp(operation, "failure", started);
                if (attempt == maxAttempts) throw transportFailure(operation, unavailable);
                last = transportFailure(operation, unavailable);
                retry(operation, attempt);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                recordHttp(operation, "failure", started);
                throw new BillingClientException("Billing-One request interrupted", 503,
                        BillingErrorCode.BILLING_UNAVAILABLE, true, interrupted);
            }
        }
        throw last == null ? new BillingClientException("Billing-One unavailable", 503,
                BillingErrorCode.BILLING_UNAVAILABLE, true) : last;
    }

    private BillingClientException transportFailure(String operation, Throwable cause) {
        if (cause instanceof BillingClientException typed) return typed;
        return new BillingClientException("Billing-One transport/JSON failure", 502,
                BillingErrorCode.INTERNAL_ERROR, true, cause);
    }

    private void retry(String operation, int attempt) {
        recordRetry(operation, "retry");
        try {
            Thread.sleep(Math.min(100L, 25L * attempt));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new BillingClientException("Billing-One retry interrupted", 503,
                    BillingErrorCode.BILLING_UNAVAILABLE, true, interrupted);
        }
    }

    private void recordHttp(String operation, String result, long started) {
        if (metrics == null) return;
        try {
            metrics.counter("billing_http_requests_total", "operation", operation, "result", result).increment();
            metrics.timer("billing_http_request_duration_seconds", "operation", operation, "result", result)
                    .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        } catch (RuntimeException ignored) {
            // Telemetry must never change the Billing fail-closed transport behavior.
        }
    }

    private void recordRetry(String operation, String result) {
        if (metrics == null) return;
        try { metrics.counter("billing_http_retries_total", "operation", operation, "result", result).increment(); }
        catch (RuntimeException ignored) { }
    }

    private HttpRequest.Builder authenticatedRequest(String path, BillingRequestMetadata metadata) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(configuration.endpoint(path))
                .timeout(configuration.requestTimeout())
                .header("X-Billing-Service", metadata.callerService())
                .header("X-Billing-Service-Credential", configuration.serviceCredential());
        if (metadata.billingSubjectAssertion() != null)
            builder.header("Authorization", "Bearer " + metadata.billingSubjectAssertion());
        return builder;
    }

    private static String operation(String path) {
        if (path.contains("/decisions")) return "decision";
        if (path.contains("/usage-events")) return "usage";
        if (path.contains("/grants")) return "grant";
        if (path.contains("/revocations")) return "revoke";
        if (path.contains("/adjustments")) return "adjustment";
        return "request";
    }

    private static String result(BillingClientException error) {
        if (error.status() == 408 || error.status() == 504) return "timeout";
        return error.status() >= 500 ? "failure" : "rejected";
    }

    @FunctionalInterface
    private interface HttpCall { HttpResponse<String> send() throws IOException, InterruptedException; }

    @FunctionalInterface
    private interface ResponseParser<T> { T parse(HttpResponse<String> response) throws IOException; }

    private static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid query value", e);
        }
    }

    private BillingClientException error(int status, String body) {
        BillingErrorCode code = BillingErrorCode.INTERNAL_ERROR;
        try {
            ErrorBody parsed = json.readValue(body, ErrorBody.class);
            if (parsed.errorCode != null) code = BillingErrorCode.valueOf(parsed.errorCode);
        } catch (Exception ignored) {
            // Preserve the HTTP status and convert unknown payloads to a typed transport error.
        }
        boolean retryable = status == 408 || status == 429 || status >= 500;
        return new BillingClientException("Billing-One rejected request (HTTP " + status + ")", status,
                code, retryable);
    }

    private static final class ErrorBody {
        @com.fasterxml.jackson.annotation.JsonProperty("error_code")
        public String errorCode;
    }

    private static final class TransactionBody {
        public java.util.List<BillingTransaction> transactions;
    }

    private static final class ExpiringBody {
        public java.util.List<java.util.Map<String, Object>> lots;
    }
}
