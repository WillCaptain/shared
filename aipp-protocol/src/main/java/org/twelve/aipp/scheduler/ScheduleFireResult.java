package org.twelve.aipp.scheduler;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

/** AIPP acknowledgement for a scheduled delivery. */
public record ScheduleFireResult(Status status, @JsonProperty("retry_at") Long retryAt, String error) {
    public enum Status {
        COMPLETED("completed"), RETRY("retry"), CANCELLED("cancelled");

        private final String wireValue;

        Status(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonValue
        public String wireValue() {
            return wireValue;
        }
    }

    public ScheduleFireResult {
        if (status == null) throw new IllegalArgumentException("status is required");
        if (status == Status.RETRY && (retryAt == null || retryAt <= 0)) {
            throw new IllegalArgumentException("retryAt is required for RETRY");
        }
        if (status != Status.RETRY && retryAt != null) {
            throw new IllegalArgumentException("retryAt is only valid for RETRY");
        }
        error = error == null ? "" : error.trim();
    }

    public static ScheduleFireResult completed() {
        return new ScheduleFireResult(Status.COMPLETED, null, "");
    }

    public static ScheduleFireResult retryAt(long epochMillis, String error) {
        return new ScheduleFireResult(Status.RETRY, epochMillis, error);
    }

    public static ScheduleFireResult cancelled() {
        return new ScheduleFireResult(Status.CANCELLED, null, "");
    }
}
