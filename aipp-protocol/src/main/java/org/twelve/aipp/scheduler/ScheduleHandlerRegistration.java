package org.twelve.aipp.scheduler;

/** One callback handler exposed by an AIPP through {@code GET /api/schedules}. */
public record ScheduleHandlerRegistration(String name) {
    public ScheduleHandlerRegistration {
        name = AippScheduleSpec.requireHandler(name);
    }

    public String callbackPath() {
        return AippScheduleSpec.firePath(name);
    }
}
