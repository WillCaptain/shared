package org.twelve.shared.trace;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TraceEventTest {
    @Test
    void parsesBrowserWireFormatAndPreservesSchemaV0() {
        Instant timestamp = Instant.parse("2026-07-28T03:00:00Z");
        TraceEvent event = TraceEvent.fromMap(Map.ofEntries(
                Map.entry("schema_version", 0),
                Map.entry("trace_id", "trace-1"),
                Map.entry("parent_id", "parent-1"),
                Map.entry("correlation_id", "corr-1"),
                Map.entry("user_id", "user-1"),
                Map.entry("ts", timestamp.toString()),
                Map.entry("actor", "user"),
                Map.entry("surface", "host_ui"),
                Map.entry("action", "workspace.set"),
                Map.entry("target", Map.of("document_id", "doc-1")),
                Map.entry("outcome", "ok")));

        assertEquals(0, event.schemaVersion());
        assertEquals(0, event.toMap().get("schema_version"));
        assertEquals("workspace.set", event.action());
        assertEquals(timestamp, event.ts());
        assertFalse(event.toMap().containsKey("schemaVersion"));
    }

    @Test
    void acceptsStringSchemaAndProvisionalAliasesAtTheIngestBoundary() {
        TraceEvent event = TraceEvent.fromMap(Map.of(
                "schemaVersion", "0",
                "id", "old-id",
                "timestamp", "2026-07-28T03:00:00Z",
                "context", Map.of("request", true),
                "metadata", Map.of("response", true)));

        assertEquals(0, event.schemaVersion());
        assertEquals("old-id", event.id());
        assertEquals(event.ts(), event.timestamp());
        assertEquals(true, event.context().get("request"));
        assertEquals(true, event.metadata().get("response"));
    }

    @Test
    void suppliesSafeIngestDefaultsAndRejectsInvalidConstructedEvents() {
        TraceEvent parsed = TraceEvent.fromMap(Map.of("action", "test"));
        assertNotNull(parsed.traceId());
        assertEquals(parsed.traceId(), parsed.correlationId());
        assertEquals("unknown", parsed.userId());
        assertThrows(IllegalArgumentException.class, () -> new TraceEvent(
                0, "", null, "corr", "user", Instant.now(), "system", "host", "test",
                Map.of(), Map.of(), Map.of(), "ok"));
    }

    @Test
    void sanitizesPayloadsBeforeSerialization() {
        TraceEvent event = TraceEvent.fromMap(Map.of(
                "action", "tool.proxy",
                "request", Map.of("token", "secret", "tool", "search")));

        assertEquals(Map.of("tool", "search"), event.request());
        assertEquals(Map.of("tool", "search"), event.toMap().get("request"));
    }

    @Test
    void malformedTimestampFallsBackWithoutBreakingTheWireContract() {
        TraceEvent event = TraceEvent.fromMap(Map.of(
                "trace_id", "trace-invalid-time",
                "ts", "not-an-instant",
                "action", "test"));

        assertNotNull(event.ts());
        assertDoesNotThrow(() -> Instant.parse((String) event.toMap().get("ts")));
    }
}
