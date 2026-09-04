package org.twelve.aipp.userprofile;

import java.util.List;
import java.util.Map;

/**
 * Public, provider-neutral user-profile capability owned by Ones shared protocol.
 * Consumers depend on these tool names / {@link #INTERFACE_TYPE}, never on a
 * provider {@code app_id}.
 */
public final class UserProfileCapabilitySpec {
    public static final String INTERFACE_TYPE = "shared.user.profile/v1";
    public static final String PROFILE_VIEW_TOOL = "user_profile_view";
    public static final String FIND_USER_TOOL = "find_user";
    public static final String GET_PRINCIPAL_TOOL = "get_principal";
    public static final String RUNTIME_TOOL = "user_profile_runtime";
    public static final int SCHEMA_VERSION = 1;

    private UserProfileCapabilitySpec() {}

    public static Map<String, Object> effect(String appId, String moduleUrl) {
        if (appId == null || appId.isBlank()) throw new IllegalArgumentException("app_id is required");
        if (moduleUrl == null || moduleUrl.isBlank()) throw new IllegalArgumentException("module_url is required");
        return Map.of(
                "type", INTERFACE_TYPE,
                "payload", Map.of(
                        "schema_version", SCHEMA_VERSION,
                        "app_id", appId.trim(),
                        "module_url", moduleUrl.trim(),
                        "operations", List.of(PROFILE_VIEW_TOOL, FIND_USER_TOOL, GET_PRINCIPAL_TOOL)));
    }

    public static void assertPublicProfile(Map<String, ?> profile) {
        if (profile == null || text(profile.get("id")).isBlank()) {
            throw new IllegalArgumentException("profile.id is required");
        }
        if (text(profile.get("name")).isBlank()) {
            throw new IllegalArgumentException("profile.name is required");
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
