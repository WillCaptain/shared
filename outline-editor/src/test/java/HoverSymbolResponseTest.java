import org.junit.jupiter.api.Test;
import org.twelve.shared.outline.editor.StatelessOutlineEditor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HoverSymbolResponseTest {
    @Test
    void wraps_symbol_for_client() {
        String code = "let x = 1;";
        int off = code.indexOf('x') + 1;
        Map<String, Object> body = StatelessOutlineEditor.hoverSymbolResponse("", code, off);
        assertFalse(body.isEmpty());
        @SuppressWarnings("unchecked")
        Map<String, Object> sym = (Map<String, Object>) body.get("symbol");
        assertNotNull(sym);
        assertEquals("x", sym.get("name"));
        assertNotNull(sym.get("type"));
    }

    @Test
    void empty_when_no_symbol() {
        assertTrue(StatelessOutlineEditor.hoverSymbolResponse("", "   ", 0).isEmpty());
    }
}
