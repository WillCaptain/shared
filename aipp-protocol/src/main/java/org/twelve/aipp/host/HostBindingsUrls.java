package org.twelve.aipp.host;

/** Resolve Host URLs from {@link HostUrlResolver} with optional injected bindings. */
public final class HostBindingsUrls {

    private HostBindingsUrls() {}

    /** {@code {host_base_url}/api/world-events} for entitir {@link HostEventPublisher}-style POST. */
    public static String worldEventsUrl(HostBindingsStore store, String fallbackHostBaseUrl) {
        String base = hostBaseUrl(store, fallbackHostBaseUrl);
        if (base.isBlank()) return "";
        return base + "/api/world-events";
    }

    public static String hostBaseUrl(HostBindingsStore store, String fallback) {
        return HostUrlResolver.resolveFromStore(store, fallback);
    }
}
