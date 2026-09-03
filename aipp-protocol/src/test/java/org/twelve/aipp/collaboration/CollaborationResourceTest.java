package org.twelve.aipp.collaboration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollaborationResourceTest {
    @Test
    void defines_provider_neutral_graph_capabilities() {
        var resource = new CollaborationResource(CollaborationResource.SCHEMA, "graph", "g-1",
                "Architecture", 1, CollaborationResource.GRAPH_OPEN_TOOL,
                Map.of("graph_id", "g-1"), Map.of("authorized_by", "will"));
        assertThat(resource.openTool()).isEqualTo("collaboration_graph_open");
        assertThat(resource.openArguments()).containsEntry("graph_id", "g-1");
    }

    @Test
    void rejects_unversioned_resources() {
        assertThatThrownBy(() -> new CollaborationResource(CollaborationResource.SCHEMA, "graph", "g-1",
                "Architecture", 0, CollaborationResource.GRAPH_OPEN_TOOL, Map.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
