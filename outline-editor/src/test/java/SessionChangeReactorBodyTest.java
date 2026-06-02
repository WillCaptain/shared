import org.junit.jupiter.api.Test;
import org.twelve.shared.outline.editor.StatelessOutlineEditor;

import java.util.List;
import java.util.Map;

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

    /** Mirrors {@code ReactorOutlinePreamble#wrapBody} (single event lambda + typed each). */
    private static String wrapBody(String body) {
        String inner = body.replaceAll("\\bchanges\\.each", "event.changes.each")
                .replaceAll(
                        "(\\bevent\\.changes\\.each\\s*\\(\\s*)([A-Za-z_][A-Za-z0-9_]*)\\s*->",
                        "$1($2: OntologyEntityChange)->");
        return """
                (event: SessionChangeEvent) -> {
                %s
                }
                """.formatted(inner);
    }

    @Test
    void wrapped_listener_body_has_no_inference_markers() {
        List<Map<String, Object>> markers = StatelessOutlineEditor.validateMarkers(PRELUDE, wrapBody(USER_BODY));
        assertTrue(markers.isEmpty(), "unexpected markers: " + markers);
    }
}
