package org.twelve.aipp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class AippFreePlanSpecTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final AippFreePlanSpec spec = new AippFreePlanSpec();

    @Test
    void validatesDirectClarifyAndDagCompilationModes() throws Exception {
        assertThatNoException().isThrownBy(() -> spec.assertValidGoalCompilation(JSON.readTree("""
                {"schema_version":"worldone.goal_compilation/v1","mode":"direct",
                 "normalized_goal":"weather tomorrow","constraints":[],
                 "direct_query":{"primary":"weather tomorrow","alternates":[]}}
                """)));
        assertThatNoException().isThrownBy(() -> spec.assertValidGoalCompilation(JSON.readTree("""
                {"schema_version":"worldone.goal_compilation/v1","mode":"clarify",
                 "normalized_goal":"send it","question":"Who should receive it?","missing":["recipient"]}
                """)));
        assertThatNoException().isThrownBy(() -> spec.assertValidGoalCompilation(JSON.readTree("""
                {"schema_version":"worldone.goal_compilation/v1","mode":"dag",
                 "normalized_goal":"onboard Sarah and assign laptop","constraints":[],
                 "dag":%s}
                """.formatted(validDag()))));
    }

    @Test
    void rejectsCycleBadBindingDuplicateAndInvalidStatus() throws Exception {
        JsonNode cycle = JSON.readTree(validDag()
                .replace("\"depends_on\":[]", "\"depends_on\":[\"asset\"]"));
        assertThatThrownBy(() -> spec.assertValidFreePlanDag(cycle))
                .hasMessageContaining("cycle");

        JsonNode badBinding = JSON.readTree(validDag()
                .replace("\"from_node\":\"person\"", "\"from_node\":\"missing\""));
        assertThatThrownBy(() -> spec.assertValidFreePlanDag(badBinding))
                .hasMessageContaining("unknown node");

        JsonNode duplicate = JSON.readTree(validDag()
                .replace("\"id\":\"asset\"", "\"id\":\"person\""));
        assertThatThrownBy(() -> spec.assertValidFreePlanDag(duplicate))
                .hasMessageContaining("duplicate");

        JsonNode status = JSON.readTree(validDag()
                .replaceFirst("\"status\":\"pending\"", "\"status\":\"mystery\""));
        assertThatThrownBy(() -> spec.assertValidFreePlanDag(status))
                .hasMessageContaining("invalid node status");
    }

    @Test
    void validatesV2SysPlanPayload() throws Exception {
        JsonNode payload = JSON.readTree("""
                {"schema_version":"worldone.free_plan/v2","plan_id":"p-1","revision":2,
                 "status":"awaiting_approval","nodes":[],"edges":[],"evidence":[],
                 "requires_approval":true,"can_execute":true}
                """);
        assertThatNoException().isThrownBy(() -> spec.assertValidSysPlanPayload(payload));
        org.assertj.core.api.Assertions.assertThat(AippSystemWidget.PLAN).isEqualTo("sys.plan");
    }

    private static String validDag() {
        return """
                {"schema_version":"worldone.free_plan/v2","plan_id":"p-1","revision":1,
                 "goal":"onboard Sarah and assign laptop","source_message":"same","status":"draft",
                 "scope":{"app_ids":[],"capability_ids":[],"allows_new_read_only":true,
                          "allows_new_mutations":false},
                 "nodes":[
                   {"id":"person","question":"create Sarah","depends_on":[],"inputs":{},
                    "success":{"required_outputs":["person.id"],"semantic_condition":"created"},
                    "risk":"read_only","status":"pending","attempt":0},
                   {"id":"asset","question":"assign laptop","depends_on":["person"],
                    "inputs":{"person_id":{"from_node":"person","output":"person.id"}},
                    "success":{"required_outputs":["asset.id"],"semantic_condition":"assigned"},
                    "risk":"mutation","status":"pending","attempt":0}],
                 "edges":[{"from":"person","to":"asset","type":"data"}],"evidence":[]}
                """;
    }
}
