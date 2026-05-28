import org.junit.jupiter.api.Test;
import org.twelve.gcp.exception.GCPError;
import org.twelve.shared.outline.editor.StatelessOutlineEditor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class StatelessOutlineEditorTest {

    private static final String SYS = EditorTestFixtures.SYSTEM_OUTLINES;

    private static final String PREAMBLE = SYS + """
            outline Computer = { name: String, serial_num: String };
            outline Employee = { name: String, computer: Unit -> Computer? };
            outline Employees = VirtualSet<Employee>{
              create: Employee -> Employee
            };
            let employees = __ontology_repo__<Employees>;
            """;

    private static final String IDEMPOTENT_PREAMBLE = SYS + """
            outline ItemStatus = PENDING|APPROVED|EXPIRED;
            outline Badge = { status: ItemStatus };
            outline Badges = VirtualSet<Badge>{
              create: {status: ItemStatus} -> Badge,
              issue_badge: Unit -> Unit
            };
            outline Employee = { name: String };
            let badges = __ontology_repo__<Badges>;
            """;

    private static final String IDEMPOTENT_USER = wrapped("""
            let badge = badges.create({status = ItemStatus.APPROVED});
            badge
            """);

    private static String wrapped(String body) {
        return "(employee: Employee) -> {\n" + body + "\n}\n";
    }

    @Test
    void validate_does_not_report_not_initialized_for_create_with_param() {
        List<Map<String, Object>> markers = StatelessOutlineEditor.validateMarkers(
                PREAMBLE, wrapped("employees.create(employee);"));
        Set<String> msgs = markers.stream()
                .map(m -> String.valueOf(m.get("message")))
                .collect(Collectors.toSet());
        assertFalse(msgs.stream().anyMatch(m -> m.contains("used before initialization")),
                "unexpected NOT_INITIALIZED: " + msgs);
    }

    @Test
    void completions_after_employees_computer_chain() {
        String user = wrapped("employees.computer().");
        int offset = user.indexOf("employees.computer().") + "employees.computer().".length();
        List<Map<String, Object>> items = StatelessOutlineEditor.completionsWire(PREAMBLE, user, offset);
        Set<String> labels = items.stream()
                .map(m -> String.valueOf(m.get("label")))
                .collect(Collectors.toSet());
        assertTrue(labels.contains("name"), "expected Computer.name, got " + labels);
        assertTrue(labels.contains("serial_num"), "expected Computer.serial_num, got " + labels);
    }

    @Test
    void inferWithPrelude_surfaces_gcp_errors_on_fork() {
        var si = StatelessOutlineEditor.inferWithPrelude(PREAMBLE, wrapped("employees.create(employee);"));
        assertNotNull(si);
        Set<String> msgs = si.asf().allErrors().stream()
                .map(GCPError::displayMessage)
                .collect(Collectors.toSet());
        assertFalse(msgs.stream().anyMatch(m -> m.contains("used before initialization")),
                "unexpected NOT_INITIALIZED: " + msgs);
    }

    @Test
    void validate_then_infer_then_validate_is_stable_for_same_input() {
        String combined = IDEMPOTENT_PREAMBLE + IDEMPOTENT_USER;
        Map<String, Object> body = Map.of("prelude_length", IDEMPOTENT_PREAMBLE.length());

        List<Map<String, Object>> before = StatelessOutlineEditor.validateCombinedWire(body, combined);
        String inferred = StatelessOutlineEditor.inferReturnType(IDEMPOTENT_PREAMBLE, IDEMPOTENT_USER);
        List<Map<String, Object>> after = StatelessOutlineEditor.validateCombinedWire(body, combined);

        assertEquals(markerMessages(before), markerMessages(after),
                "validate result must be idempotent across infer side effects, inferred=" + inferred);
        assertFalse(containsVariantMismatch(after),
                "must not drift to APPROVED/PENDING mismatch after infer: " + markerMessages(after));
    }

    @Test
    void infer_then_validate_then_infer_is_stable_for_same_input() {
        String first = StatelessOutlineEditor.inferReturnType(IDEMPOTENT_PREAMBLE, IDEMPOTENT_USER);
        List<Map<String, Object>> markers = StatelessOutlineEditor.validateMarkers(IDEMPOTENT_PREAMBLE, IDEMPOTENT_USER);
        String second = StatelessOutlineEditor.inferReturnType(IDEMPOTENT_PREAMBLE, IDEMPOTENT_USER);

        assertEquals(first, second, "inferReturnType should be stable across validate/infer ordering");
        assertFalse(containsVariantMismatch(markers),
                "validate must not introduce enum-variant drift after infer: " + markerMessages(markers));
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
}
