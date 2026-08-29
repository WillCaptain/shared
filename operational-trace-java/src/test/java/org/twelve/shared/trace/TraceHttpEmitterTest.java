package org.twelve.shared.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceHttpEmitterTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void postsAnActualTraceBatchToTheHostEndpoint() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        CompletableFuture<String> receivedBody = new CompletableFuture<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/trace/events", exchange -> {
            method.set(exchange.getRequestMethod());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedBody.complete(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();

        TraceEvent event = new TraceEvent(
                TraceEvent.SCHEMA_VERSION, "trace-1", null, "corr-1", "001",
                Instant.parse("2026-07-28T03:00:00Z"), "system", TraceSurfaces.NOTE_ONE,
                TraceActions.SYNC_PLAN, Map.of("workspace_scope", "notes"),
                Map.of(), Map.of(), TraceOutcomes.OK);
        TraceHttpEmitter emitter =
                new TraceHttpEmitter("http://127.0.0.1:" + server.getAddress().getPort() + "/");

        emitter.emitAsync(event).get(5, TimeUnit.SECONDS);

        assertEquals("POST", method.get());
        assertEquals("application/json", contentType.get());
        JsonNode body = new ObjectMapper().readTree(receivedBody.get(5, TimeUnit.SECONDS));
        assertEquals("trace-1", body.at("/events/0/trace_id").asText());
        assertEquals("sync.plan", body.at("/events/0/action").asText());
        assertEquals(0, body.at("/events/0/schema_version").asInt());
        assertEquals("notes", body.at("/events/0/target/workspace_scope").asText());
    }

    @Test
    void transportFailureNeverEscapesToTheCaller() throws Exception {
        TraceHttpEmitter emitter = new TraceHttpEmitter("http://127.0.0.1:1");
        emitter.emitAsync(TraceEvent.fromMap(Map.of("action", "test")))
                .get(5, TimeUnit.SECONDS);
    }

    @Test
    void exactEndpointAndNonSuccessResponseRemainBestEffort() throws Exception {
        CompletableFuture<Boolean> received = new CompletableFuture<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/trace/events", exchange -> {
            received.complete(true);
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();

        TraceHttpEmitter emitter = new TraceHttpEmitter(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/api/trace/events");
        emitter.emitAsync(TraceEvent.fromMap(Map.of("action", "test")))
                .get(5, TimeUnit.SECONDS);

        assertTrue(received.get(5, TimeUnit.SECONDS));
    }

    @Test
    void nullEventIsAnImmediateNoOp() throws Exception {
        TraceHttpEmitter emitter = new TraceHttpEmitter("http://127.0.0.1:1");
        emitter.emitAsync(null).get(1, TimeUnit.SECONDS);
    }
}
