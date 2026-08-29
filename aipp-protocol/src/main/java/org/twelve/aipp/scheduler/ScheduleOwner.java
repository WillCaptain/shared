package org.twelve.aipp.scheduler;

/** Authenticated namespace for scheduled jobs. */
public record ScheduleOwner(String appId, String userId) {
    public ScheduleOwner {
        if (appId == null || appId.isBlank()) throw new IllegalArgumentException("appId is required");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId is required");
        appId = appId.trim();
        userId = userId.trim();
    }
}
