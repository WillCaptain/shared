package org.twelve.aipp.host;

/** Shared server-to-server Host notification interface used by independent AIPPs. */
public final class HostNotificationSpec {
    public static final String PUBLISH_PATH = "/api/host/integrations/notifications/publish-lead";
    public static final String ACT_PATH = "/api/host/integrations/notifications/act";
    public static final String DISMISS_OCCURRENCE_PATH = "/api/host/integrations/notifications/dismiss-occurrence";
    public static final String KIND_CALENDAR_DUE = "calendar.due";
    public static final String ATTENTION = "attention";

    private HostNotificationSpec() {}

    public static String stingGroupKey(String stingKey) {
        String key = stingKey == null ? "" : stingKey.trim();
        if (key.isBlank()) throw new IllegalArgumentException("sting key is required");
        return "sting:" + key;
    }
}
