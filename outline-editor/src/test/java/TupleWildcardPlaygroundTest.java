import org.junit.jupiter.api.Test;
import org.twelve.shared.outline.editor.StatelessOutlineEditor;

import static org.junit.jupiter.api.Assertions.*;

class TupleWildcardPlaygroundTest {
    @Test
    void tuple_wildcard_lambda_has_no_spurious_errors() {
        String code = "let f = (x: (?, ?)) -> (x.1, x.0);";
        var markers = StatelessOutlineEditor.validateMarkers("", code);
        assertTrue(markers.isEmpty(), "markers: " + markers);
    }

    @Test
    void hover_works_on_locals_when_meta_misses() {
        String code = "let a:Int? = 100; let b = (a as Int).to_str();";
        int bOffset = code.indexOf("b") + 1;
        var hover = StatelessOutlineEditor.hoverSymbol("", code, bOffset);
        assertNotNull(hover);
        assertTrue(String.valueOf(hover.get("type")).contains("String"));
    }
}
