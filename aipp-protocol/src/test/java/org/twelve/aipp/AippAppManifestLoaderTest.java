package org.twelve.aipp;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AippAppManifestLoaderTest {

    @Test
    void loadClasspath_missingResource_returnsEmpty() {
        assertThat(AippAppManifestLoader.loadClasspath("/no-such-aipp-app.json")).isEmpty();
    }

    @Test
    void withConfiguration_mergesBlock() {
        Map<String, Object> out = AippAppManifestLoader.withConfiguration(
                Map.of("app_id", "demo", "app_name", "Demo"),
                Map.of("ui", Map.of("layout", Map.of())));
        assertThat(out).containsEntry("app_id", "demo");
        assertThat(out).containsKey("configuration");
    }
}
