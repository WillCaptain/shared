package org.twelve.aipp.invoke;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Decision session push event name and catalog response helpers.
 *
 * @see spec/decision-reactor-invoke.md
 * @see spec/ontology-world-catalog.md
 */
public class DecisionReactorInvokeSpec {

    public static final String EVENT_ONTOLOGY_SESSION_CHANGE = "ontology_session_change";

    private final OntologyWorldCatalogSpec catalog = new OntologyWorldCatalogSpec();

    public void assertValidWorldsResponse(JsonNode body) {
        catalog.assertValidWorldsResponse(body);
    }

    public void assertValidEntryTemplatesResponse(JsonNode body, String worldId) {
        catalog.assertValidEntryTemplatesResponse(body, worldId);
    }
}
