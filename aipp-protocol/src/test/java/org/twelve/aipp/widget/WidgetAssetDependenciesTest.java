package org.twelve.aipp.widget;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.twelve.aipp.AippAppSpec;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class WidgetAssetDependenciesTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void exactStaticTargetsAndOptionalLegacyManifests() {
        var spec = new AippAppSpec();
        spec.assertValidWidgetStructure(json.valueToTree(Map.of("type", "sample")));
        for (String path : List.of("/widgets/shared/detail.js", "/widgets/base.css?v=2")) {
            assertTrue(WidgetAssetPaths.isStaticTarget(path));
            spec.assertValidWidgetStructure(json.valueToTree(Map.of("type", "sample",
                    "render", Map.of("assets", List.of(path)))));
        }
    }

    @Test void ambiguousOrNonStaticTargetsAreNotPermissions() {
        for (String path : List.of("https://example.com/widgets/a.js", "//example.com/widgets/a.js",
                "/api/approve.js", "/widgets/../a.js", "/widgets/%2e%2e/a.js", "/widgets/a.js;x=1",
                "/widgets/a.js#x", "/widgets/*.js", "/widgets/a//b.js", "/widgets/.hidden/a.js",
                "/widgets/中文.js", "/widgets/a\\b.js", "/widgets/a.js\n", "/widgets/a.json")) {
            assertFalse(WidgetAssetPaths.isStaticTarget(path), path);
            assertThrows(AssertionError.class, () -> new AippAppSpec().assertValidWidgetStructure(
                    json.valueToTree(Map.of("type", "sample", "render", Map.of("assets", List.of(path))))));
        }
    }

    @Test void malformedDuplicateAndOversizedDeclarationsFailValidation() {
        for (Object assets : List.of("/widgets/a.js", List.of(1), List.of("/widgets/a.js", "/widgets/a.js"),
                java.util.Collections.nCopies(129, "/widgets/a.js"))) {
            assertThrows(AssertionError.class, () -> new AippAppSpec().assertValidWidgetStructure(
                    json.valueToTree(Map.of("type", "sample", "render", Map.of("assets", assets)))));
        }
        assertFalse(WidgetAssetPaths.isStaticTarget("/widgets/a.js?v=" + "x".repeat(2048)));
        assertFalse(WidgetAssetPaths.isStaticTarget(null));
        assertThrows(AssertionError.class, () -> new AippAppSpec().assertValidWidgetStructure(
                json.readTree("{\"type\":\"sample\",\"render\":{\"assets\":null}}")));
    }
}
