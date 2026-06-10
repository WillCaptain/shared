package org.twelve.shared.outline.editor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dict.each callback param completions via inference (not host hardcode). */
class DictEachInferenceTest {

    @Test
    void dictEach_untypedParam_infersValueType() {
        String prelude = """
                outline FieldDelta = { before: Any?, after: Any? };
                """;
        String code = "let m: [String: FieldDelta] = {};\nm.each(f->f.";
        int off = code.length();
        List<String> labels = StatelessOutlineEditor.completionsWire(prelude, code, off).stream()
                .map(m -> String.valueOf(m.get("label")))
                .collect(Collectors.toList());
        assertTrue(labels.contains("before"), "labels: " + labels);
        assertTrue(labels.contains("after"), "labels: " + labels);
    }
}
