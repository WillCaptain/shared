import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.twelve.shared.outline.editor.StatelessOutlineEditor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for non-idempotent infer/validate against the real
 * world-eai-onboarding editor preamble (SYSTEM_OUTLINES + entity schema).
 */
class DecisionEditorPreambleIdempotencyTest {

    private static String PREAMBLE;

    private static final String ACTION_USER = """
            (employee: Employee) -> {
            let badge = badges.create({status = ItemStatus.APPROVED});
            badge
            }
            """;

    @BeforeAll
    static void loadPreamble() throws IOException {
        String entitySchema;
        try (InputStream in = DecisionEditorPreambleIdempotencyTest.class
                .getResourceAsStream("/decision-editor-preamble.outline")) {
            assertNotNull(in, "missing classpath resource decision-editor-preamble.outline");
            entitySchema = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(3367, entitySchema.length(),
                "entity schema snapshot must stay 3367 bytes for wire regression");
        // Matches world-entitir OutlineEditorManifestController.editorPreamble().
        PREAMBLE = EditorTestFixtures.SYSTEM_OUTLINES + entitySchema;
        assertTrue(PREAMBLE.contains("outline VirtualSet"),
                "manifest preamble must include world SYSTEM_OUTLINES");
    }

    @Test
    void inferReturnType_does_not_mutate_cached_preamble() {
        String itemStatusBefore = itemStatusVariants(StatelessOutlineEditor.preambleAsf(PREAMBLE));
        assertTrue(itemStatusBefore.contains("PENDING"), itemStatusBefore);
        assertTrue(itemStatusBefore.contains("APPROVED"), itemStatusBefore);

        StatelessOutlineEditor.inferReturnType(PREAMBLE, ACTION_USER);

        String itemStatusAfter = itemStatusVariants(StatelessOutlineEditor.preambleAsf(PREAMBLE));
        assertEquals(itemStatusBefore, itemStatusAfter,
                "cached preamble must stay immutable across inferReturnType");
    }

    @Test
    void validate_then_infer_then_validate_with_real_preamble() {
        Map<String, Object> body = Map.of("prelude_length", PREAMBLE.length());
        String combined = PREAMBLE + ACTION_USER;

        List<Map<String, Object>> before = StatelessOutlineEditor.validateCombinedWire(body, combined);
        String inferred = StatelessOutlineEditor.inferReturnType(PREAMBLE, ACTION_USER);
        List<Map<String, Object>> after = StatelessOutlineEditor.validateCombinedWire(body, combined);

        assertEquals(markerMessages(before), markerMessages(after),
                "markers drifted after inferReturnType; inferred=" + inferred
                        + " before=" + markerMessages(before)
                        + " after=" + markerMessages(after));
        assertFalse(containsVariantMismatch(after),
                "must not report enum variant drift: " + markerMessages(after));
    }

    @Test
    void infer_then_validate_then_infer_with_real_preamble() {
        String first = StatelessOutlineEditor.inferReturnType(PREAMBLE, ACTION_USER);
        List<Map<String, Object>> markers = StatelessOutlineEditor.validateMarkers(PREAMBLE, ACTION_USER);
        String second = StatelessOutlineEditor.inferReturnType(PREAMBLE, ACTION_USER);

        assertEquals(first, second,
                "inferReturnType must be stable; markers=" + markerMessages(markers));
        assertFalse(containsVariantMismatch(markers),
                "validate after infer must not drift: " + markerMessages(markers));
    }

    @Test
    void repeated_calls_with_real_preamble_remain_consistent() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> tasks = java.util.stream.IntStream.range(0, 32)
                    .mapToObj(i -> (Callable<String>) () -> {
                        List<Map<String, Object>> markers =
                                StatelessOutlineEditor.validateMarkers(PREAMBLE, ACTION_USER);
                        String ret = StatelessOutlineEditor.inferReturnType(PREAMBLE, ACTION_USER);
                        return ret + " | " + String.join(" || ", markerMessages(markers));
                    })
                    .toList();

            Set<String> signatures = pool.invokeAll(tasks).stream()
                    .map(f -> {
                        try {
                            return f.get(60, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toSet());

            assertEquals(1, signatures.size(),
                    "parallel calls must converge to one signature: " + signatures);
        } finally {
            pool.shutdownNow();
        }
    }

    private static List<String> markerMessages(List<Map<String, Object>> markers) {
        return markers.stream()
                .map(m -> String.valueOf(m.get("message")))
                .sorted()
                .toList();
    }

    private static boolean containsVariantMismatch(List<Map<String, Object>> markers) {
        return markerMessages(markers).stream()
                .anyMatch(m -> m.contains("is not a variant"));
    }

    private static String itemStatusVariants(org.twelve.gcp.ast.ASF asf) {
        for (var ast : asf.asts()) {
            var root = ast.symbolEnv().root();
            var sym = root.lookupOutline("ItemStatus");
            if (sym == null) sym = root.lookupSymbol("ItemStatus");
            if (sym != null) {
                var o = sym.outline().eventual();
                if (o instanceof org.twelve.gcp.outline.adt.Option opt) {
                    return opt.options().stream().map(Object::toString).sorted()
                            .reduce((a, b) -> a + "|" + b).orElse(o.toString());
                }
                return o.toString();
            }
        }
        return "<missing>";
    }
}
