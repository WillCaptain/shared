import org.junit.jupiter.api.Test;
import org.twelve.gcp.meta.ModuleMeta;
import org.twelve.shared.outline.editor.StatelessOutlineEditor;

import static org.junit.jupiter.api.Assertions.*;

class HoverTypeDisplayTest {
    @Test
    void hover_any_dict_and_module_default() throws Exception {
        String code = """
                module default
                var c: Any = 100;
                let d1: [String : ?] = ["Will": 30, 30: 30];
                let d3: [?:?] = ["Will": 30];
                """;

        var markers = StatelessOutlineEditor.validateMarkers("", code);
        assertFalse(markers.stream().anyMatch(m ->
                String.valueOf(m.get("message")).contains("Internal error")));

        var si = StatelessOutlineEditor.inferWithPrelude("", code);
        assertNotNull(si);
        ModuleMeta meta = si.ast().meta();

        int cOff = code.indexOf("c") + 1;
        var cHover = StatelessOutlineEditor.hoverSymbol("", code, cOff);
        assertNotNull(cHover);
        assertEquals("any", String.valueOf(cHover.get("type")));
        assertNotEquals("c", cHover.get("type"));
        assertEquals("any", meta.resolve("c", cOff).type().toLowerCase());

        int d1Off = code.indexOf("d1") + 1;
        var d1Hover = StatelessOutlineEditor.hoverSymbol("", code, d1Off);
        assertNotNull(d1Hover);
        assertTrue(String.valueOf(d1Hover.get("type")).contains("String"));
        assertNotEquals("d1", d1Hover.get("type"));

        int d3Off = code.lastIndexOf("d3") + 1;
        var d3Hover = StatelessOutlineEditor.hoverSymbol("", code, d3Off);
        assertNotNull(d3Hover);
        assertTrue(String.valueOf(d3Hover.get("type")).contains("String"));
        assertNotEquals("d1", d3Hover.get("type"));
        assertNotEquals("d3", d3Hover.get("type"));
    }
}
