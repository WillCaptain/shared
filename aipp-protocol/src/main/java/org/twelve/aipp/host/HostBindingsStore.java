package org.twelve.aipp.host;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * In-memory Host runtime bindings ({@code PUT /api/host/bindings}).
 * Not persisted — Host re-injects on install / env change.
 */
public final class HostBindingsStore {

    private final ConcurrentHashMap<String, Object> bindings = new ConcurrentHashMap<>();
    private volatile Consumer<Map<String, Object>> onChange;

    public void setOnChange(Consumer<Map<String, Object>> listener) {
        this.onChange = listener;
    }

    /** Shallow-merge top-level keys from {@code patch} into the in-memory store. */
    public synchronized Map<String, Object> merge(Map<String, Object> patch) {
        if (patch == null || patch.isEmpty()) {
            return snapshot();
        }
        for (Map.Entry<String, Object> e : patch.entrySet()) {
            String key = e.getKey();
            if (key == null || key.isBlank()) continue;
            Object value = e.getValue();
            if (value == null) {
                bindings.remove(key);
            } else {
                bindings.put(key, value);
            }
        }
        Map<String, Object> snap = snapshot();
        Consumer<Map<String, Object>> cb = onChange;
        if (cb != null) {
            try {
                cb.accept(snap);
            } catch (RuntimeException ignored) {
                // listener failures must not break PUT
            }
        }
        return snap;
    }

    public Map<String, Object> snapshot() {
        return new LinkedHashMap<>(bindings);
    }

    public Object get(String key) {
        if (key == null) return null;
        return bindings.get(key);
    }

    public String getString(String key, String fallback) {
        Object v = get(key);
        if (v == null) return fallback == null ? "" : fallback;
        String s = String.valueOf(v).trim();
        return s.isBlank() ? (fallback == null ? "" : fallback) : s;
    }

    public boolean isEmpty() {
        return bindings.isEmpty();
    }
}
