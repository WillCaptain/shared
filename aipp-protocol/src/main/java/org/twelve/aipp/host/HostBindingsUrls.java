package org.twelve.aipp.host;

/** Resolve Host URLs from injected {@link HostBindingsStore} bindings. */
public final class HostBindingsUrls {

    private HostBindingsUrls() {}

    /** {@code {host_base_url}/api/world-events} for entitir {@link HostEventPublisher}-style POST. */
    public static String worldEventsUrl(HostBindingsStore store, String fallbackHostBaseUrl) {
        String base = store == null ? "" : store.getString("host_base_url", fallbackHostBaseUrl);
        if (base.isBlank()) return "";
        return normalizeBase(base) + "/api/world-events";
    }

    public static String hostBaseUrl(HostBindingsStore store, String fallback) {
        if (store == null) return fallback == null ? "" : fallback.trim();
        return store.getString("host_base_url", fallback);
    }

    private static String normalizeBase(String base) {
        String s = base.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
