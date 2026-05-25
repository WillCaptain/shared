package org.twelve.aipp.host;

import java.util.LinkedHashMap;
import java.util.Map;

/** Shared handlers for {@code GET/PUT /api/host/bindings}. */
public final class HostBindingsSupport {

    private HostBindingsSupport() {}

    public static Map<String, Object> put(HostBindingsStore store, Map<String, Object> body) {
        if (store == null) {
            return Map.of("ok", false, "error", "host_bindings_store_unavailable");
        }
        if (body == null || body.isEmpty()) {
            return Map.of("ok", false, "error", "request body required");
        }
        store.merge(body);
        return Map.of("ok", true);
    }

    public static Map<String, Object> get(HostBindingsStore store) {
        if (store == null) {
            return Map.of("ok", false, "error", "host_bindings_store_unavailable");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("bindings", store.snapshot());
        return out;
    }
}
