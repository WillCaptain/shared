import org.junit.jupiter.api.Test;
import org.twelve.shared.outline.diagnostic.OutlineSyntaxDiagnostics;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class OutlineSyntaxDiagnosticsTest {

    @Test
    void eof_missing_semicolon_should_point_to_eof_slot() {
        String code = """
                let f = x->{
                  100
                }""";
        String msg = "token: END:\\$ doesn't match any grammar definition at line: 3, position from 1 to 2";
        Map<String, Object> marker = OutlineSyntaxDiagnostics.parseMarker(msg, code);

        assertEquals("Missing ';' at end of statement.", marker.get("message"));
        assertEquals(3, marker.get("startLine"));
        assertEquals(1, marker.get("startColumn"));
    }

    @Test
    void eof_missing_paren_should_be_rewritten() {
        String code = "let a = (";
        String msg = "token: END:\\$ doesn't match any grammar definition at line: 1, position from 9 to 10";
        Map<String, Object> marker = OutlineSyntaxDiagnostics.parseMarker(msg, code);

        assertEquals("Unexpected end of input; missing ')'.", marker.get("message"));
    }

    @Test
    void extra_closing_delimiter_should_be_rewritten() {
        String code = "let a = 1);";
        String msg = "token: RPAREN:) doesn't match any grammar definition at line: 1, position from 9 to 10";
        Map<String, Object> marker = OutlineSyntaxDiagnostics.parseMarker(msg, code);

        assertEquals("Unexpected ')': remove extra closing delimiter.", marker.get("message"));
    }

    @Test
    void normalize_should_remove_raw_eof_noise_when_actionable_hint_exists() {
        List<Map<String, Object>> markers = List.of(
                Map.of("message", "Missing ';' after block expression.", "startLine", 3, "startColumn", 1, "endLine", 3, "endColumn", 2, "severity", 8),
                Map.of("message", "token: END:\\$ doesn't match any grammar definition", "startLine", 3, "startColumn", 31, "endLine", 3, "endColumn", 32, "severity", 8)
        );
        List<Map<String, Object>> normalized = OutlineSyntaxDiagnostics.normalizeMarkers(markers);
        assertEquals(1, normalized.size());
        assertEquals("Missing ';' after block expression.", normalized.getFirst().get("message"));
    }
}
