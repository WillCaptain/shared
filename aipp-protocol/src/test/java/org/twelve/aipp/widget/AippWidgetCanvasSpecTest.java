package org.twelve.aipp.widget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AippWidgetCanvasSpecTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AippWidgetSpec spec = new AippWidgetSpec();

    @Test
    void readyCanvasDeclaresSearchableSpecification() throws Exception {
        JsonNode widget = json.readTree("""
                {"type":"board","display_mode":"canvas","canvas_spec":{
                  "version":"1.0","status":"ready","url":"/widgets/board/canvas-spec.md",
                  "schema_url":"/widgets/board/canvas-schema.json","search_tool":"board_spec_search"}}
                """);
        assertThatCode(() -> spec.assertCanvasWidgetDeclaresSpecification(widget)).doesNotThrowAnyException();
    }

    @Test
    void placeholderMayOmitSearchTool() throws Exception {
        JsonNode widget = json.readTree("""
                {"type":"board","display_mode":"canvas","canvas_spec":{
                  "version":"1.0","status":"placeholder","url":"/widgets/board/canvas-spec.md"}}
                """);
        assertThatCode(() -> spec.assertCanvasWidgetDeclaresSpecification(widget)).doesNotThrowAnyException();
    }

    @Test
    void readyCanvasWithoutSearchToolIsRejected() throws Exception {
        JsonNode widget = json.readTree("""
                {"type":"board","display_mode":"canvas","canvas_spec":{
                  "version":"1.0","status":"ready","url":"/widgets/board/canvas-spec.md"}}
                """);
        assertThatThrownBy(() -> spec.assertCanvasWidgetDeclaresSpecification(widget))
                .isInstanceOf(AssertionError.class);
    }
}
