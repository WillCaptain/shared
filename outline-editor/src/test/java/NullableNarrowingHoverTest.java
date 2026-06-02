import org.junit.jupiter.api.Test;
import org.twelve.shared.outline.editor.StatelessOutlineEditor;

import static org.junit.jupiter.api.Assertions.*;

class NullableNarrowingHoverTest {
    @Test
    void optional_unknown_and_nullable_let_var_hover_types() throws Exception {
        String code = """
                let e: ?? = 100;
                var e_1: ?? = 100;
                let s: String? = "hi";
                var s_1: String? = "hi";
                """;

        assertEquals("Integer", hoverType(code, "e"));
        assertEquals("Integer?", hoverType(code, "e_1"));
        assertEquals("String", hoverType(code, "s"));
        assertEquals("String?", hoverType(code, "s_1"));
    }

    private static String hoverType(String code, String name) {
        String needle = " " + name + ":";
        int idx = code.indexOf(needle);
        int off = (idx >= 0 ? idx + 1 : code.indexOf(name)) + 1;
        var hover = StatelessOutlineEditor.hoverSymbol("", code, off);
        assertNotNull(hover, "hover for " + name);
        return String.valueOf(hover.get("type"));
    }
}
