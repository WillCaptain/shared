package org.twelve.aipp.collaboration;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VersionedResourceGrantTest {
    @Test
    void pinsVersionAndScopesRecipientsActionsAndExpiry() {
        var resource = new VersionedResource(VersionedResource.SCHEMA, "note-one", "doc-1", "7",
                "sha256:abc", "Plan.md", "text/markdown", 42L, "8",
                VersionedResource.openToolFor("note-one"),
                Map.of("resource_id", "doc-1", "version_id", "7"));
        var grant = new ScopedResourceGrant(ScopedResourceGrant.SCHEMA, "grant-1", "chat-1", "will",
                List.of("qu-ling"), resource.resourceId(), resource.versionId(),
                List.of("view", "comment"), Instant.parse("2026-09-04T00:00:00Z"), null, "confirm-1");

        assertThat(resource.versionId()).isEqualTo("7");
        assertThat(resource.currentVersionId()).isEqualTo("8");
        assertThat(grant.activeAt(Instant.parse("2026-09-03T00:00:00Z"))).isTrue();
    }

    @Test
    void rejects_implicit_or_unbounded_grants() {
        assertThatThrownBy(() -> new ScopedResourceGrant(ScopedResourceGrant.SCHEMA, "g", "c", "u",
                List.of(), "r", "v", List.of("write"), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
