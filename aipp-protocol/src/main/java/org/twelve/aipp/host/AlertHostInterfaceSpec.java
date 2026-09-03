package org.twelve.aipp.host;

import java.util.LinkedHashMap;
import java.util.Map;

/** Generic Host runtime contract supplied by whichever AIPP owns alert projection. */
public final class AlertHostInterfaceSpec {
    public static final String EFFECT_TYPE = "shared.alert.runtime/v1";

    private AlertHostInterfaceSpec() {}

    public static Map<String, Object> effect(String appId, String moduleUrl) {
        if (appId == null || appId.isBlank()) throw new IllegalArgumentException("appId required");
        if (moduleUrl == null || moduleUrl.isBlank()) throw new IllegalArgumentException("moduleUrl required");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("app_id", appId.trim());
        payload.put("module_url", moduleUrl.trim());
        return Map.of("type", EFFECT_TYPE, "payload", payload);
    }
}
