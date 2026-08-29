package org.twelve.aipp.scheduler;

import java.util.regex.Pattern;

/** Shared constants and validation rules for the AIPP scheduler protocol. */
public final class AippScheduleSpec {
    public static final String HANDLERS_PATH = "/api/schedules";
    public static final String FIRE_PATH_PREFIX = "/api/schedules/";
    public static final String HOST_JOBS_PATH = "/api/host/schedules";
    public static final String HOST_APP_ID_HEADER = "X-AIPP-App-Id";
    public static final String HOST_USER_ID_HEADER = "X-AIPP-User-Id";
    public static final String DELIVERY_ID_HEADER = "X-AIPP-Schedule-Delivery-Id";

    public static final int MAX_HANDLER_LENGTH = 80;
    public static final int MAX_JOB_KEY_LENGTH = 200;

    private static final Pattern HANDLER = Pattern.compile("[a-z][a-z0-9_.-]{0,79}");

    private AippScheduleSpec() {}

    public static String requireHandler(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!HANDLER.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "handler must match [a-z][a-z0-9_.-]{0,79}");
        }
        return normalized;
    }

    public static String requireJobKey(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_JOB_KEY_LENGTH) {
            throw new IllegalArgumentException("jobKey must contain 1..200 characters");
        }
        return normalized;
    }

    public static long requireFireAt(long value) {
        if (value <= 0) throw new IllegalArgumentException("fireAt must be a positive epoch millisecond");
        return value;
    }

    public static String firePath(String handler) {
        return FIRE_PATH_PREFIX + requireHandler(handler);
    }
}
