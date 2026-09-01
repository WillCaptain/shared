package org.twelve.aipp.scheduler;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

/** AIPP acknowledgement for a scheduled delivery. */
public record ScheduleFireResult(Status status, @JsonProperty("retry_at") Long retryAt, String error) {
    public enum Status {
        COMPLETED("completed"),
        RETRYABLE_FAILED("retryable_failed"),
        TERMINAL_FAILED("terminal_failed"),
        CANCELLED("cancelled");

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
        if (status == Status.RETRYABLE_FAILED && (retryAt == null || retryAt <= 0)) {
            throw new IllegalArgumentException("retryAt is required for a retryable failure");
        }
        if (status != Status.RETRYABLE_FAILED && retryAt != null) {
            throw new IllegalArgumentException("retryAt is only valid for a retryable failure");
        }
        error = error == null ? "" : error.trim();
        if ((status == Status.RETRYABLE_FAILED || status == Status.TERMINAL_FAILED)
                && error.isBlank()) {
            throw new IllegalArgumentException("error is required for a failed delivery");
        }
    }

    public static ScheduleFireResult completed() {
        return new ScheduleFireResult(Status.COMPLETED, null, "");
    }

    public static ScheduleFireResult retryableFailed(long epochMillis, String error) {
        return new ScheduleFireResult(Status.RETRYABLE_FAILED, epochMillis, error);
    }

    public static ScheduleFireResult terminalFailed(String error) {
        return new ScheduleFireResult(Status.TERMINAL_FAILED, null, error);
    }

    public static ScheduleFireResult cancelled() {
        return new ScheduleFireResult(Status.CANCELLED, null, "");
    }
}
