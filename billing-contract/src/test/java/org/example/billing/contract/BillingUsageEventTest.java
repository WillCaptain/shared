package org.example.billing.contract;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class BillingUsageEventTest {
    @Test void accepts_authenticated_aipp_origins_without_a_hard_coded_app_list() {
        for (String origin : new String[]{"world-one", "note-one", "memory-one", "ontology-rag"})
            assertEquals(origin, event("world-one", origin).originService());
        assertEquals("ontology-rag", event("ontology-rag", null).originService());
        assertEquals("ontology-rag", event("world-one", " ontology-rag ").originService());
    }

    @Test void rejects_origins_that_do_not_fit_the_service_identity_or_database_column() {
        for (String invalid : new String[]{"Ontology-rag", "ontology_rag", "-app", "app/path", "x".repeat(81)})
            assertThrows(IllegalArgumentException.class, () -> event("world-one", invalid));
    }

    private static BillingUsageEvent event(String service, String origin) {
        return new BillingUsageEvent("event", "decision", "call", "invocation", service, origin, "user",
                "embedding", "feature", "provider", "model", null, "unknown", true, false, null,
                Instant.EPOCH, Instant.EPOCH);
    }
}
