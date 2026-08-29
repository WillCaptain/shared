package org.twelve.aipp.scheduler;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;

/** Stable public view of a Host-owned scheduled job. */
public record ScheduleJob(
        String id,
        @JsonProperty("job_key") String jobKey,
        String handler,
        @JsonProperty("fire_at") long fireAt,
        ScheduleLevel level,
        State state,
        int attempt,
        Map<String, Object> payload
) {
    public enum State {
        PENDING("pending"), LEASED("leased"), COMPLETED("completed"),
        CANCELLED("cancelled"), DEAD_LETTER("dead_letter");

        private final String wireValue;

        State(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonValue
        public String wireValue() {
            return wireValue;
        }
    }

    public ScheduleJob {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
        id = id.trim();
        jobKey = AippScheduleSpec.requireJobKey(jobKey);
        handler = AippScheduleSpec.requireHandler(handler);
        fireAt = AippScheduleSpec.requireFireAt(fireAt);
        level = level == null ? ScheduleLevel.COARSE : level;
        if (state == null) throw new IllegalArgumentException("state is required");
        if (attempt < 0) throw new IllegalArgumentException("attempt cannot be negative");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public ScheduleJob(String id, String jobKey, String handler, long fireAt,
                       State state, int attempt, Map<String, Object> payload) {
        this(id, jobKey, handler, fireAt, ScheduleLevel.COARSE, state, attempt, payload);
    }
}
