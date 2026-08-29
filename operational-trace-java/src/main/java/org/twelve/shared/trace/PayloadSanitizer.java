package org.twelve.shared.trace;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Trims and redacts trace payloads before persistence, transport, or export. */
public final class PayloadSanitizer {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_STRING = 512;
    private static final int MAX_JSON_BYTES = 4096;
    private static final Pattern SECRET_KEY = Pattern.compile(
            "(?i)(api[_-]?key|token|password|authorization|secret|cookie|bearer)");

    private PayloadSanitizer() {}

    public static Map<String, Object> sanitizeMap(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) return Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (isSecretKey(entry.getKey())) continue;
            out.put(entry.getKey(), sanitizeValue(entry.getValue()));
        }
        return truncateMap(out);
    }

    private static Object sanitizeValue(Object value) {
        if (value == null) return null;
        if (value instanceof String string) return truncateString(string);
        if (value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> typed = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                if (key != null && !isSecretKey(String.valueOf(key))) {
                    typed.put(String.valueOf(key), sanitizeValue(item));
                }
            });
            return typed;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object item : list) out.add(sanitizeValue(item));
            return out;
        }
        return truncateString(String.valueOf(value));
    }

    private static Map<String, Object> truncateMap(Map<String, Object> map) {
        try {
            byte[] bytes = JSON.writeValueAsBytes(map);
            if (bytes.length <= MAX_JSON_BYTES) {
                return Collections.unmodifiableMap(new LinkedHashMap<>(map));
            }
            return Map.of("_truncated", true, "_bytes", bytes.length, "_keys", List.copyOf(map.keySet()));
        } catch (Exception ignored) {
            return Map.of("_truncated", true, "_error", "serialize_failed");
        }
    }

    private static String truncateString(String value) {
        if (value.length() <= MAX_STRING) return value;
        return value.substring(0, MAX_STRING) + "…";
    }

    private static boolean isSecretKey(String key) {
        return key != null && SECRET_KEY.matcher(key).find();
    }
}
