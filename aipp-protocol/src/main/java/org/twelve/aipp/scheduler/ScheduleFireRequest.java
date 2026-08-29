package org.twelve.aipp.scheduler;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/** Host-to-AIPP callback body for one leased delivery attempt. */
public record ScheduleFireRequest(
        @JsonProperty("job_id") String jobId,
        @JsonProperty("job_key") String jobKey,
        String handler,
        @JsonProperty("fire_at") long fireAt,
        int attempt,
        Map<String, Object> payload
) {
    public ScheduleFireRequest {
        if (jobId == null || jobId.isBlank()) throw new IllegalArgumentException("jobId is required");
        jobId = jobId.trim();
        jobKey = AippScheduleSpec.requireJobKey(jobKey);
        handler = AippScheduleSpec.requireHandler(handler);
        fireAt = AippScheduleSpec.requireFireAt(fireAt);
        if (attempt < 1) throw new IllegalArgumentException("attempt must be at least 1");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
