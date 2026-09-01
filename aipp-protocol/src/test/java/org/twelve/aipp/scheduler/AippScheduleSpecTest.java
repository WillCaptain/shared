package org.twelve.aipp.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AippScheduleSpecTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void derivesFixedCallbackPathFromValidatedHandler() {
        ScheduleHandlerRegistration registration =
                new ScheduleHandlerRegistration("reminder.due");

        assertThat(registration.callbackPath()).isEqualTo("/api/schedules/reminder.due");
        assertThatThrownBy(() -> new ScheduleHandlerRegistration("https://other.example/fire"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesAndCopiesJobRequest() {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("id", "42");
        ScheduleJobRequest request =
                new ScheduleJobRequest("reminder:42", "reminder.due", 1L, payload);
        payload.put("id", "changed");

        assertThat(request.payload()).containsEntry("id", "42");
        assertThatThrownBy(() -> new ScheduleJobRequest(" ", "reminder.due", 1L, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScheduleJobRequest("key", "Reminder Due", 1L, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScheduleJobRequest("key", "reminder.due", 0L, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retryAcknowledgementRequiresFutureTimestampShape() {
        assertThat(ScheduleFireResult.completed().status())
                .isEqualTo(ScheduleFireResult.Status.COMPLETED);
        assertThat(ScheduleFireResult.retryableFailed(100L, "busy").retryAt()).isEqualTo(100L);
        assertThatThrownBy(() -> new ScheduleFireResult(
                ScheduleFireResult.Status.RETRYABLE_FAILED, null, "busy"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScheduleFireResult(
                ScheduleFireResult.Status.COMPLETED, 100L, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exposesExplicitFailureOutcomes() {
        assertThat(ScheduleFireResult.retryableFailed(200L, "notification unavailable").status())
                .isEqualTo(ScheduleFireResult.Status.RETRYABLE_FAILED);
        assertThat(ScheduleFireResult.terminalFailed("invalid payload").status())
                .isEqualTo(ScheduleFireResult.Status.TERMINAL_FAILED);
        assertThatThrownBy(() -> ScheduleFireResult.retryableFailed(200L, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScheduleFireResult.terminalFailed(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void serializesWireFieldsAsNormativeSnakeCase() throws Exception {
        String request = json.writeValueAsString(
                new ScheduleJobRequest("reminder:42", "reminder.due", 100L, Map.of()));
        String result = json.writeValueAsString(
                ScheduleFireResult.retryableFailed(300L, "notification unavailable"));

        assertThat(request).contains("\"job_key\":\"reminder:42\"")
                .contains("\"fire_at\":100")
                .contains("\"level\":\"coarse\"");
        assertThat(result).contains("\"status\":\"retryable_failed\"")
                .contains("\"retry_at\":300");
    }

    @Test
    void definesTheThreeNormativeCadences() throws Exception {
        assertThat(ScheduleLevel.COARSE.intervalMillis()).isEqualTo(15_000);
        assertThat(ScheduleLevel.NORMAL.intervalMillis()).isEqualTo(8_000);
        assertThat(ScheduleLevel.PRECISE.intervalMillis()).isEqualTo(1_000);
        assertThat(json.readValue("\"precise\"", ScheduleLevel.class))
                .isEqualTo(ScheduleLevel.PRECISE);
        assertThatThrownBy(() -> ScheduleLevel.fromWireValue("fast"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultsMissingLevelToLegacyFifteenSecondCadence() throws Exception {
        ScheduleJobRequest request = json.readValue("""
                {"job_key":"legacy:42","handler":"legacy.fire","fire_at":100,"payload":{}}
                """, ScheduleJobRequest.class);

        assertThat(request.level()).isEqualTo(ScheduleLevel.COARSE);
        assertThat(request.level().intervalMillis()).isEqualTo(15_000);
    }
}
