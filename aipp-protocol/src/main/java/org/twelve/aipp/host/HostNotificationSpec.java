package org.twelve.aipp.host;

/** Shared server-to-server Host notification interface used by independent AIPPs. */
public final class HostNotificationSpec {
    public static final String CONTRACT_VERSION = "v1";
    public static final String PUBLISH_PATH = "/api/host/integrations/notifications/publish";
    public static final String UPDATE_PATH = "/api/host/integrations/notifications/update";
    public static final String ACT_PATH = "/api/host/integrations/notifications/act";
    public static final String DISMISS_PATH = "/api/host/integrations/notifications/dismiss";
    public static final String RESOLVE_PATH = "/api/host/integrations/notifications/resolve";
    public static final String DISMISS_OCCURRENCE_PATH = "/api/host/integrations/notifications/dismiss-occurrence";
    public static final String KIND_CALENDAR_DUE = "calendar.due";
    public static final String ATTENTION = "attention";

    private HostNotificationSpec() {}

    public static String groupKey(String owner, String resourceId) {
        String namespace = owner == null ? "" : owner.trim();
        String resource = resourceId == null ? "" : resourceId.trim();
        if (namespace.isBlank()) throw new IllegalArgumentException("notification owner is required");
        if (resource.isBlank()) throw new IllegalArgumentException("notification resource id is required");
        return namespace + ":" + resource;
    }
}
