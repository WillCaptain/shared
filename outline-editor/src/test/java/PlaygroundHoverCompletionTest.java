import org.junit.jupiter.api.Test;
import org.twelve.shared.outline.editor.StatelessOutlineEditor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Regression: 12th playground (empty prelude) hover + dot completions. */
class PlaygroundHoverCompletionTest {

    @Test
    void hover_shows_type_for_let_binding() {
        String code = "let x = [1, 2, 3];";
        int off = code.indexOf('x') + 1;
        Map<String, Object> resp = StatelessOutlineEditor.hoverSymbolResponse("", code, off);
        assertFalse(resp.isEmpty(), "hover response: " + resp);
        @SuppressWarnings("unchecked")
        Map<String, Object> sym = (Map<String, Object>) resp.get("symbol");
        assertNotNull(sym);
        assertEquals("x", sym.get("name"));
        assertNotNull(sym.get("type"));
        assertFalse(String.valueOf(sym.get("type")).isBlank());
    }

    @Test
    void array_dot_completion_includes_each() {
        String code = "let x = [1, 2, 3];\nx.";
        int off = code.length();
        List<Map<String, Object>> items = StatelessOutlineEditor.completionsWire("", code, off);
        List<String> labels = items.stream()
                .map(m -> String.valueOf(m.get("label")))
                .collect(Collectors.toList());
        assertTrue(labels.contains("each"), "labels: " + labels);
    }

    @Test
    void hover_on_outline_type_name() {
        String code = "outline Color = Red | Green;\nlet x = 1;";
        int off = code.indexOf("Color") + 2;
        Map<String, Object> resp = StatelessOutlineEditor.hoverSymbolResponse("", code, off);
        assertFalse(resp.isEmpty(), "hover on type name: " + resp);
    }

    @Test
    void hover_on_lambda_param_in_each() {
        String code = """
                outline Item = { name: String };
                let xs = [Item{name: "a"}];
                xs.each(c->c.name);
                """;
        int off = code.indexOf("c->") + 1;
        Map<String, Object> resp = StatelessOutlineEditor.hoverSymbolResponse("", code, off);
        assertFalse(resp.isEmpty(), "hover on each callback param: " + resp);
    }

    @Test
    void entity_dot_completion_includes_fields() {
        String code = """
                outline Person = { name: String, age: Int };
                let p = Person{name: "a", age: 1};
                p.""";
        int off = code.length();
        List<Map<String, Object>> items = StatelessOutlineEditor.completionsWire("", code, off);
        List<String> labels = items.stream()
                .map(m -> String.valueOf(m.get("label")))
                .collect(Collectors.toList());
        assertTrue(labels.contains("name"), "labels: " + labels);
        assertTrue(labels.contains("age"), "labels: " + labels);
    }

    @Test
    void sum_value_dot_shows_tags_and_to_str() {
        String code = """
                outline EntityChangeOperation = CREATE | UPDATE | DELETE;
                outline OntologyEntityChange = { operation: EntityChangeOperation };
                let f = (x:[OntologyEntityChange])->{ x.each(c->c.operation.); };""";
        int dot = code.lastIndexOf('.') + 1;
        List<Map<String, Object>> items = StatelessOutlineEditor.completionsWire("", code, dot);
        List<String> labels = items.stream()
                .map(m -> String.valueOf(m.get("label")))
                .collect(Collectors.toList());
        assertTrue(labels.contains("to_str"), "expected to_str in " + labels);
        assertTrue(labels.contains("CREATE"), "expected CREATE in " + labels);
        assertTrue(labels.contains("UPDATE"), "expected UPDATE in " + labels);
        assertTrue(labels.contains("DELETE"), "expected DELETE in " + labels);
    }

    @Test
    void hover_on_method_name_in_member_chain() {
        String code = """
                outline EntityChangeOperation = CREATE | UPDATE | DELETE;
                outline OntologyEntityChange = { operation: EntityChangeOperation };
                let f = (x:[OntologyEntityChange])->{ x.each(c->c.operation.to_str()); };""";
        int eachOff = code.indexOf(".each") + 1;
        Map<String, Object> eachHover = StatelessOutlineEditor.hoverSymbolResponse("", code, eachOff);
        assertFalse(eachHover.isEmpty(), "each hover: " + eachHover);
        @SuppressWarnings("unchecked")
        Map<String, Object> eachSym = (Map<String, Object>) eachHover.get("symbol");
        assertEquals("each", eachSym.get("name"));
        assertEquals("method", eachSym.get("kind"));
        assertTrue(String.valueOf(eachSym.get("type")).contains("Unit"),
                "each type: " + eachSym.get("type"));

        int toStrOff = code.indexOf(".to_str") + 1;
        Map<String, Object> toStrHover = StatelessOutlineEditor.hoverSymbolResponse("", code, toStrOff);
        assertFalse(toStrHover.isEmpty(), "to_str hover: " + toStrHover);
        @SuppressWarnings("unchecked")
        Map<String, Object> toStrSym = (Map<String, Object>) toStrHover.get("symbol");
        assertEquals("to_str", toStrSym.get("name"));
        assertEquals("method", toStrSym.get("kind"));
    }
}
