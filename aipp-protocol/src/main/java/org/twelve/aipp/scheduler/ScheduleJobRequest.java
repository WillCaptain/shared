package org.twelve.aipp.scheduler;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/** AIPP-to-Host request to create or replace one one-shot scheduled job. */
public record ScheduleJobRequest(
        @JsonProperty("job_key") String jobKey,
        String handler,
        @JsonProperty("fire_at") long fireAt,
        ScheduleLevel level,
        Map<String, Object> payload
) {
    public ScheduleJobRequest {
        jobKey = AippScheduleSpec.requireJobKey(jobKey);
        handler = AippScheduleSpec.requireHandler(handler);
        fireAt = AippScheduleSpec.requireFireAt(fireAt);
        level = level == null ? ScheduleLevel.COARSE : level;
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public ScheduleJobRequest(String jobKey, String handler, long fireAt, Map<String, Object> payload) {
        this(jobKey, handler, fireAt, ScheduleLevel.COARSE, payload);
    }
}
