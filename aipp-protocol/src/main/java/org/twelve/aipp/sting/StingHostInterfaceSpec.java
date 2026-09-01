package org.twelve.aipp.sting;

import java.util.Map;

/** Public browser/runtime contract exported by the Sting owner AIPP. */
public final class StingHostInterfaceSpec {
    public static final String EFFECT_TYPE = "shared.sting.runtime/v1";
    public static final String LAUNCHER_EFFECT_TYPE = "shared.sting.launcher/v1";
    public static final String FLASH_EFFECT_TYPE = "shared.sting.flash/v1";
    public static final String HELP_EFFECT_TYPE = "shared.sting.help/v1";
    public static final int SCHEMA_VERSION = 1;
    public static final String WALL_WIDGET_TYPE = "sting-wall";
    public static final String CARD_WIDGET_TYPE = "sting-card";
    public static final String COUNTDOWN_WIDGET_TYPE = "sting-countdown-card";

    private StingHostInterfaceSpec() {}

    public static Map<String, Object> effect(String appId, String moduleUrl) {
        return effect(EFFECT_TYPE, appId, moduleUrl);
    }

    public static Map<String, Object> effect(String type, String appId, String moduleUrl) {
        if (!java.util.Set.of(EFFECT_TYPE, LAUNCHER_EFFECT_TYPE, FLASH_EFFECT_TYPE, HELP_EFFECT_TYPE)
                .contains(type)) throw new IllegalArgumentException("unsupported Sting interface type");
        if (appId == null || appId.isBlank()) throw new IllegalArgumentException("app_id is required");
        if (moduleUrl == null || moduleUrl.isBlank()) throw new IllegalArgumentException("module_url is required");
        return Map.of(
                "type", type,
                "payload", Map.of(
                        "schema_version", SCHEMA_VERSION,
                        "app_id", appId.trim(),
                        "module_url", moduleUrl.trim(),
                        "wall_widget_type", WALL_WIDGET_TYPE,
                        "card_widget_type", CARD_WIDGET_TYPE,
                        "countdown_widget_type", COUNTDOWN_WIDGET_TYPE));
    }
}
