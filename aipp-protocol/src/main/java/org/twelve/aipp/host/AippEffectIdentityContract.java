package org.twelve.aipp.host;

/**
 * Opaque completed-effect identity. Host stores and replays strings; the owning
 * AIPP computes them. Host must not interpret operator tables, OS families, or
 * provider tool names to derive an identity.
 */
public final class AippEffectIdentityContract {
    public static final String FIELD = "effect_identity";
    public static final String PATH_SUFFIX = "/effect-identity";
    public static final String REQUEST_ARGS = "args";
    public static final String REQUEST_PLATFORM = "platform";
    public static final String RESPONSE_OK = "ok";
    public static final String RESPONSE_IDENTITY = "identity";
    public static final String ERROR_CLASS = "effect_already_completed";
    public static final String STATUS_REPLAYED = "replayed";

    private AippEffectIdentityContract() {}

    public static String path(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("tool name is required");
        }
        String name = toolName.trim();
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("tool name must be a single segment");
        }
        return "/api/tools/" + name + PATH_SUFFIX;
    }
}
