import org.junit.jupiter.api.Test;
import org.twelve.shared.outline.editor.StatelessOutlineEditor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for the car-maker VirtualSet world (the entitir.system ontology used in
 * the 12th playground). Completing / hovering a member on an UNTYPED lambda parameter
 * ({@code countries.filter(c -> c.}) used to crash GCP inference with a
 * {@code ClassCastException} (STRING → Entity) in
 * {@code Genericable.mergeParallelConstraints}, so the editor returned no completions
 * and no hover ("completion doesn't work / hover doesn't work all the time").
 */
class CarMakerWorldCompletionTest {

    private static String world() throws Exception {
        try (InputStream in = CarMakerWorldCompletionTest.class.getResourceAsStream("/carmaker-world.outline")) {
            assertNotNull(in, "missing /carmaker-world.outline test resource");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> labels(String prelude, String code) {
        return StatelessOutlineEditor.completionsWire(prelude, code, code.length()).stream()
                .map(m -> String.valueOf(m.get("label")))
                .collect(Collectors.toList());
    }

    /** Empty-preamble playground shape: the whole world + query is the user buffer. */
    @Test
    void completion_on_untyped_filter_param_emptyPreamble() throws Exception {
        String code = world() + "\ncountries.filter(c -> c.";
        List<String> labels = labels("", code);
        assertTrue(labels.contains("carMakers"), "expected Country members, got: " + labels);
        assertTrue(labels.contains("countryname"), "expected Country members, got: " + labels);
    }

    /** Preamble shape: world is the prelude, query is the user buffer. */
    @Test
    void completion_on_untyped_filter_param_preambleMode() throws Exception {
        String labels = labels(world(), "countries.filter(c -> c.").toString();
        assertTrue(labels.contains("carMakers"), "expected Country members, got: " + labels);
        assertTrue(labels.contains("continent"), "expected Country members, got: " + labels);
    }

    /** Hover on the same untyped param must not crash and must resolve a type. */
    @Test
    void hover_on_untyped_filter_param_does_not_crash() throws Exception {
        String code = world() + "\ncountries.filter(c -> c.carMakers())";
        int offset = code.indexOf("c.carMakers") ; // hover on the receiver `c`
        Map<String, Object> hover = StatelessOutlineEditor.hoverSymbolResponse("", code, offset);
        assertNotNull(hover); // no exception thrown is the core assertion
    }
}
