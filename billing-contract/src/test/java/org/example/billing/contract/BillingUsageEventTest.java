package org.example.billing.contract;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class BillingUsageEventTest {
    @Test void accepts_existing_origins_and_the_new_ontology_aipp() {
        for (String origin : new String[]{"world-one", "note-one", "memory-one", "ontology-rag"})
            assertEquals(origin, event("world-one", origin).originService());
        assertEquals("ontology-rag", event("ontology-rag", null).originService());
        assertEquals("ontology-rag", event("world-one", " ontology-rag ").originService());
    }

    @Test void rejects_unregistered_and_malformed_origins() {
        for (String invalid : new String[]{"another-app", "Ontology-rag", "ontology_rag", "-app", "app/path", "x".repeat(81)})
            assertThrows(IllegalArgumentException.class, () -> event("world-one", invalid));
    }

    private static BillingUsageEvent event(String service, String origin) {
        return new BillingUsageEvent("event", "decision", "call", "invocation", service, origin, "user",
                "embedding", "feature", "provider", "model", null, "unknown", true, false, null,
                Instant.EPOCH, Instant.EPOCH);
    }
}
