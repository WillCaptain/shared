package org.twelve.aipp.tools;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CanvasResourceBindingTest {
    @Test void validatesDeclaredResourceKeysAgainstTheParameterSchema() {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var spec = new org.twelve.aipp.AippAppSpec();
        for (Object declaration : List.of("document_id", List.of("missing"), List.of("document_id", "document_id"))) {
            var invalid = json.valueToTree(Map.of(CanvasResourceBinding.FIELD, declaration,
                    "parameters", Map.of("properties", Map.of("document_id", Map.of("type", "string")))));
            assertThatThrownBy(() -> spec.assertValidCanvasResourceParameters(invalid))
                    .isInstanceOf(AssertionError.class);
        }
        spec.assertValidCanvasResourceParameters(json.valueToTree(Map.of(CanvasResourceBinding.FIELD,
                List.of("document_id"), "parameters", Map.of("properties",
                Map.of("document_id", Map.of("type", "string"))))));
    }
    private final Map<String, Object> tool = Map.of(CanvasResourceBinding.FIELD, List.of("document_id"));

    @Test void bindsAbsentBlankAndMatchingIdsWithoutMutatingInput() {
        for (Map<String, Object> args : List.<Map<String, Object>>of(Map.of(),
                Map.of("document_id", ""), Map.of("document_id", "doc-a"))) {
            assertThat(CanvasResourceBinding.bind(tool, args, "doc-a"))
                    .containsEntry("document_id", "doc-a");
        }
    }
    @Test void rejectsOtherResourcesAndNonStringIds() {
        for (Object id : List.of("doc-b", 123, List.of("doc-a"))) {
            assertThatThrownBy(() -> CanvasResourceBinding.bind(tool, Map.of("document_id", id), "doc-a"))
                    .isInstanceOf(CanvasResourceBinding.Conflict.class);
        }
    }
    @Test void commonChatAndUnboundHelpersKeepTheirArguments() {
        var args = Map.<String, Object>of("document_id", "doc-b");
        assertThat(CanvasResourceBinding.bind(tool, args, null)).isSameAs(args);
        assertThat(CanvasResourceBinding.bind(Map.of(), args, "doc-a")).isSameAs(args);
    }
}
