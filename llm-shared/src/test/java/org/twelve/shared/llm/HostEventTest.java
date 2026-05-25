package org.twelve.shared.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HostEventTest {

    @Test
    void parameterMissingHasDeterministicIdAndRoundTripsAsMap() {
        HostEvent event = HostEvent.parameterMissing(
                "world-eai-onboarding",
                "pc_pickup",
                "decision-task-onboarding_started-123",
                "task-ui-1",
                List.of("serial_number"),
                Map.of("type", "sys.parameter-missing"),
                "invoke_decision",
                Map.of("template_id", "pc_pickup"),
                Map.of(),
                List.of());

        assertThat(event.id()).startsWith("evt_pm_world_eai_onboarding_pc_pick");
        assertThat(event.id()).hasSizeLessThanOrEqualTo(80);
        assertThat(event.toMap()).containsEntry("type", "parameter_missing");
        assertThat(HostEvent.fromMap(event.toMap())).isEqualTo(event);
    }

    @Test
    void parameterMissingWritesEventLabelAndLegacyDisplayName() {
        HostEvent event = HostEvent.parameterMissing(
                "world-eai-onboarding",
                "pc_pickup",
                "scope-1",
                "ui-1",
                List.of("serial_num"),
                Map.of(),
                "invoke_decision",
                Map.of(),
                Map.of(),
                List.of(),
                "孙艺菲入职已登记");

        assertThat(event.tags().get("event_label")).isEqualTo("孙艺菲入职已登记");
        assertThat(event.tags().get("display_name")).isEqualTo("孙艺菲入职已登记");
        assertThat(event.tags().get("decision")).isEqualTo("pc_pickup");
    }
}
