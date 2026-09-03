package org.twelve.aipp.collaboration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollaborationContractTest {
    @Test void acceptsARevisionBoundTypedIntent() {
        var intent = new CollaborationActionIntent(
                CollaborationActionIntent.SCHEMA, "graph", "session-1", null,
                List.of("graph-1"), List.of("user-2"), "user-1", "my_agent", 3,
                "request-1", Map.of("prompt", "draw architecture"));
        assertThat(intent.actionType()).isEqualTo("graph");
        assertThat(intent.resourceRefs()).containsExactly("graph-1");
    }

    @Test void rejectsUnknownActionAndAudience() {
        assertThatThrownBy(() -> new CollaborationActionIntent(
                CollaborationActionIntent.SCHEMA, "magic", "session-1", null,
                List.of(), List.of(), "user-1", "world", 0, "request-1", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
