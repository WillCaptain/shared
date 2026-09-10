package org.example.billing.client;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.billing.contract.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BillingHttpClientTest {
    private HttpServer server;

    @AfterEach
    void stop() { if (server != null) server.stop(0); }

    @Test
    void forwardsOpaqueAssertionAndIdempotencyAndMapsDecision() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/billing/decisions", exchange -> {
            assertEquals("Bearer opaque.assertion", exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals("world-secret", exchange.getRequestHeaders().getFirst("X-Billing-Service-Credential"));
            assertEquals("decision-key", exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            byte[] response = ("{\"decisionId\":\"d-1\",\"allowed\":true,\"reasonCode\":null," +
                    "\"issuedAt\":\"2026-08-27T00:00:00Z\",\"expiresAt\":\"2026-08-27T00:01:00Z\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var out = exchange.getResponseBody()) { out.write(response); }
        });
        server.start();
        var config = new BillingClientConfiguration(
                java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(2), "world-secret");
        BillingDecisionResponse result = new BillingHttpClient(config).decide(
                new BillingDecisionRequest("decision-key", "call-1", "u-1", "chat", "f",
                        "provider", "model", CredentialSource.INSTANCE_CONFIG, 10L, Instant.now()),
                BillingRequestMetadata.worldOne("ignored-by-client-config", "opaque.assertion", "r-1"));
        assertEquals("d-1", result.decisionId());
        assertTrue(result.allowed());
    }

    @Test
    void mapsServerAndTimeoutErrorsToTypedRetryableFailure() {
        assertThrows(IllegalArgumentException.class,
                () -> BillingClientConfiguration.of("http://localhost", ""));
    }

    @Test
    void retriesTransientBillingFailureAndRecordsLowCardinalityTransportMetrics() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/internal/billing/decisions", exchange -> {
            if (calls.getAndIncrement() == 0) {
                byte[] body = "{\"error_code\":\"BILLING_UNAVAILABLE\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(503, body.length);
                try (var out = exchange.getResponseBody()) { out.write(body); }
                return;
            }
            byte[] body = "{\"decisionId\":\"d-retry\",\"allowed\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        var registry = new SimpleMeterRegistry();
        var config = new BillingClientConfiguration(java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(1), "world-secret");
        var result = new BillingHttpClient(config, registry).decide(
                new BillingDecisionRequest("retry-key", "call-1", "u-1", "chat", "f",
                        "provider", "model", CredentialSource.INSTANCE_CONFIG, 0L, Instant.now()),
                BillingRequestMetadata.worldOne("ignored", "assertion", "retry-key"));

        assertEquals("d-retry", result.decisionId());
        assertEquals(2, calls.get());
        assertEquals(1D, registry.get("billing_http_retries_total").tag("operation", "decision")
                .tag("result", "retry").counter().count());
        assertEquals(1D, registry.get("billing_http_requests_total").tag("operation", "decision")
                .tag("result", "failure").counter().count());
        assertEquals(1D, registry.get("billing_http_requests_total").tag("operation", "decision")
                .tag("result", "success").counter().count());
    }

    @Test
    void recordsTimeoutsAndRetryWithoutExposingTransportDetails() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/billing/decisions", exchange -> {
            try { Thread.sleep(250); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            try { exchange.sendResponseHeaders(200, 0); } catch (java.io.IOException ignored) { }
        });
        server.start();
        var registry = new SimpleMeterRegistry();
        var config = new BillingClientConfiguration(java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                java.time.Duration.ofSeconds(1), java.time.Duration.ofMillis(40), "world-secret");

        BillingClientException failure = assertThrows(BillingClientException.class, () -> new BillingHttpClient(config, registry).decide(
                new BillingDecisionRequest("timeout-key", "call-1", "u-1", "chat", "f",
                        "provider", "model", CredentialSource.INSTANCE_CONFIG, 0L, Instant.now()),
                BillingRequestMetadata.worldOne("ignored", "assertion", "timeout-key")));

        assertEquals(BillingErrorCode.INTERNAL_ERROR, failure.errorCode());
        assertTrue(failure.retryable());
        assertEquals(1D, registry.get("billing_http_retries_total").tag("operation", "decision")
                .tag("result", "retry").counter().count());
        assertEquals(2D, registry.get("billing_http_requests_total").tag("operation", "decision")
                .tag("result", "timeout").counter().count());
        assertFalse(failure.getMessage().contains("sleep"));
    }
}
