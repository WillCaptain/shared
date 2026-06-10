import org.junit.jupiter.api.Test;
import org.twelve.gcp.meta.StatelessInference;
import org.twelve.shared.outline.editor.StatelessOutlineEditor;
import static org.junit.jupiter.api.Assertions.*;

class HoverBRegressionTest {
  @Test
  void flat_body_infers_b_as_string() {
    String code = "let a:Int? = 100; let b = (a as Int).to_str();";
    int bOffset = code.indexOf("b") + 1;
    var hover = StatelessOutlineEditor.hoverSymbol("", code, bOffset);
    assertNotNull(hover, "hover b");
    assertTrue(String.valueOf(hover.get("type")).contains("String"),
        "flat b type: " + hover.get("type"));
  }

  @Test
  void wrapped_lambda_infers_b_as_string() {
    String code = "(employee: Employee) -> {\nlet a:Int? = 100; let b = (a as Int).to_str();\n}";
    int bOffset = code.indexOf("b") + 1;
    var hover = StatelessOutlineEditor.hoverSymbol("", code, bOffset);
    assertNotNull(hover, "hover b in lambda");
    assertTrue(String.valueOf(hover.get("type")).contains("String"),
        "wrapped b type: " + hover.get("type"));
  }

  @Test
  void flat_body_cast_no_spurious_warning_after_nullable_narrow() {
    String code = "let a:Int? = 100; let b = (a as Int).to_str();";
    var markers = StatelessOutlineEditor.validateMarkers("", code);
    // Definite init narrows `a` to Int, so `(a as Int)` is valid — no cast diagnostic.
    assertTrue(markers.isEmpty(), "unexpected markers: " + markers);
  }
}
