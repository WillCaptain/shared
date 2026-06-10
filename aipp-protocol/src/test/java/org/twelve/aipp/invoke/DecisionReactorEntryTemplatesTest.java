package org.twelve.aipp.invoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionReactorEntryTemplatesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void classifiesOnboardingStartedAsManualWhenManualEnabled() throws Exception {
        JsonNode d = JSON.readTree("""
                {
                  "id": "onboarding_started",
                  "manualEnabled": true,
                  "activator": {"type": "ontology", "ontologyType": "Employee"},
                  "intent": {"goal": "onboarding", "context": "onboarding"},
                  "triggers": [{"expression": "true"}]
                }
                """);
        assertThat(DecisionReactorEntryTemplates.classify(d))
                .isEqualTo(DecisionReactorEntryTemplates.ENTRY_MANUAL);
        assertThat(DecisionReactorEntryTemplates.isEntryTemplate(d)).isTrue();
        assertThat(DecisionReactorEntryTemplates.isReactorEligible(d)).isTrue();
    }

    @Test
    void excludesDecisionChainTemplate() throws Exception {
        JsonNode d = JSON.readTree("""
                {
                  "id": "issue_badge",
                  "manualEnabled": false,
                  "activator": {"type": "decision", "decisionIds": ["onboarding_started"]},
                  "triggers": [{"expression": "onboarding_started.payload.badge() is Nothing"}]
                }
                """);
        assertThat(DecisionReactorEntryTemplates.isEntryTemplate(d)).isFalse();
    }

    @Test
    void classifiesOntologyActivatorAsOntologyEntry() throws Exception {
        JsonNode d = JSON.readTree("""
                {
                  "id": "employee_listener",
                  "manualEnabled": false,
                  "activator": {"type": "ontology", "ontologyType": "Employee"},
                  "triggers": [{"expression": "event_entity.status == \\"ACTIVE\\""}]
                }
                """);
        assertThat(DecisionReactorEntryTemplates.classify(d))
                .isEqualTo(DecisionReactorEntryTemplates.ENTRY_ONTOLOGY);
        assertThat(DecisionReactorEntryTemplates.isReactorEligible(d)).isTrue();
        var rows = DecisionReactorEntryTemplates.filterEntryRows(JSON.createArrayNode().add(d));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("entry_type", DecisionReactorEntryTemplates.ENTRY_ONTOLOGY);
        assertThat(rows.get(0)).doesNotContainKey("reactor_eligible");
    }

    @Test
    void classifiesScheduleActivator() throws Exception {
        JsonNode d = JSON.readTree("""
                {
                  "id": "nightly_sync",
                  "manualEnabled": false,
                  "activator": {"type": "schedule", "cron": "0 0 * * *"},
                  "triggers": [{"expression": "true"}]
                }
                """);
        assertThat(DecisionReactorEntryTemplates.classify(d))
                .isEqualTo(DecisionReactorEntryTemplates.ENTRY_SCHEDULE);
    }
}
