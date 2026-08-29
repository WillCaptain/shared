package org.twelve.aipp.scheduler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Host-defined scheduling precision and dispatch-round cadence. */
public enum ScheduleLevel {
    COARSE("coarse", 15_000),
    NORMAL("normal", 8_000),
    PRECISE("precise", 1_000);

    private final String wireValue;
    private final long intervalMillis;

    ScheduleLevel(String wireValue, long intervalMillis) {
        this.wireValue = wireValue;
        this.intervalMillis = intervalMillis;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    public long intervalMillis() {
        return intervalMillis;
    }

    @JsonCreator
    public static ScheduleLevel fromWireValue(String value) {
        String normalized = value == null ? "" : value.trim();
        for (ScheduleLevel level : values()) {
            if (level.wireValue.equals(normalized)) return level;
        }
        throw new IllegalArgumentException("unknown schedule level: " + value);
    }
}
