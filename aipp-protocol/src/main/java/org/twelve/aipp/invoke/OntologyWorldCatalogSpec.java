package org.twelve.aipp.invoke;

import com.fasterxml.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts ontology world catalog REST responses (no capability SPI).
 *
 * @see spec/ontology-world-catalog.md
 */
public class OntologyWorldCatalogSpec {

    /** Default provider path prefix (world-entitir). */
    public static final String DEFAULT_PREFIX = "/api/decision-reactor-invoke";

    public void assertValidWorldsResponse(JsonNode body) {
        assertThat(body.path("ok").asBoolean()).isTrue();
        assertThat(body.path("worlds").isArray()).isTrue();
    }

    public void assertValidEntryTemplatesResponse(JsonNode body, String worldId) {
        assertThat(body.path("ok").asBoolean()).isTrue();
        assertThat(body.path("world_id").asText("")).isEqualTo(worldId);
        assertThat(body.path("entry_templates").isArray()).isTrue();
    }
}
