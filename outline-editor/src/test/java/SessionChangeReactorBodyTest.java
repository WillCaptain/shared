import org.junit.jupiter.api.Test;
import org.twelve.shared.outline.editor.StatelessOutlineEditor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Regression: decision-reactor wrapped listener body (SessionChangeEvent + changes.each). */
class SessionChangeReactorBodyTest {

    private static final String PRELUDE = """
            outline SessionStatus = COMPLETED | SUSPENDED | FAILED;

            outline FieldDelta = {
                  before: Any?,
                  after: Any?
            };

            outline EntityChangeOperation = CREATE | UPDATE | DELETE;

            outline OntologyEntityChange = {
                  entity_type: String,
                  entity_id: Int,
                  operation: EntityChangeOperation,
                  decision_template_id: String?,
                  fields: [String: FieldDelta]
            };

            outline SessionChangeEvent = {
                  event_id: String,
                  scope_id: String,
                  status: SessionStatus,
                  confidence: Number,
                  root_template_id: String,
                  intent_context: String?,
                  changes: [OntologyEntityChange],
                  pending_template_ids: [String]?
            };

            let external = {
                  log: (msg: String) -> {}
            };
            """;

    private static final String USER_BODY = """
            external.log("onboarding scope=" + event.scope_id + " status=" + event.status);
            changes.each(c->external.log("erp candidate " + c.entity_type + " op=" + c.operation));
            """;

    /** Mirrors {@code ReactorOutlinePreamble#wrapBody} (inner lambda exposes changes in scope). */
    private static String wrapBody(String body) {
        String inner = body;
        return """
                (event: SessionChangeEvent) -> {
                ((event: SessionChangeEvent, status: SessionStatus, confidence: Number, changes: [OntologyEntityChange]) -> {
                %s
                })(event, event.status, event.confidence, event.changes)
                }
                """.formatted(inner);
    }

    @Test
    void wrapped_listener_body_has_no_inference_markers() {
        List<Map<String, Object>> markers = StatelessOutlineEditor.validateMarkers(PRELUDE, wrapBody(USER_BODY));
        assertTrue(markers.isEmpty(), "unexpected markers: " + markers);
    }

    @Test
    void wrapped_changesEach_paramDot_uses_inference_not_hardcode() {
        String body = "changes.each(c->c.";
        String wrapped = wrapBody(body);
        int off = wrapped.indexOf("->c.") + 4;
        List<String> labels = completionLabels(PRELUDE, wrapped, off);
        assertTrue(labels.contains("entity_type"), "expected OntologyEntityChange fields, got: " + labels);
        assertTrue(labels.contains("fields"), "labels: " + labels);
    }

    @Test
    void wrapped_untypedEach_paramDot_still_completes_when_changes_is_typed() {
        String wrapped = """
                (event: SessionChangeEvent) -> {
                ((event: SessionChangeEvent, status: SessionStatus, confidence: Number, changes: [OntologyEntityChange]) -> {
                changes.each(c->c.
                })(event, event.status, event.confidence, event.changes)
                }
                """;
        int off = wrapped.indexOf("->c.") + 4;
        List<String> labels = completionLabels(PRELUDE, wrapped, off);
        assertTrue(labels.contains("entity_type"),
                "untyped c should infer from [OntologyEntityChange].each, got: " + labels);
    }

    @Test
    void wrapped_untypedFieldsEach_paramDot() {
        String wrapped = """
                (event: SessionChangeEvent) -> {
                ((event: SessionChangeEvent, status: SessionStatus, confidence: Number, changes: [OntologyEntityChange]) -> {
                changes.each(c->c.fields.each(f->f.
                })(event, event.status, event.confidence, event.changes)
                }
                """;
        int off = wrapped.lastIndexOf("->f.") + 4;
        List<String> labels = completionLabels(PRELUDE, wrapped, off);
        assertTrue(labels.contains("before") || labels.contains("after"),
                "map each param f: " + labels);
    }

    @Test
    void wrapped_fieldsEach_paramDot_uses_inference() {
        String body = "changes.each(c->c.fields.each(f->f.";
        String wrapped = wrapBody(body);
        int off = wrapped.lastIndexOf("->f.") + 4;
        List<String> labels = completionLabels(PRELUDE, wrapped, off);
        assertTrue(labels.contains("before"), "expected FieldDelta fields, got: " + labels);
        assertTrue(labels.contains("after"), "labels: " + labels);
    }

    private static List<String> completionLabels(String prelude, String code, int offset) {
        return StatelessOutlineEditor.completionsWire(prelude, code, offset).stream()
                .map(m -> String.valueOf(m.get("label")))
                .collect(Collectors.toList());
    }
}
