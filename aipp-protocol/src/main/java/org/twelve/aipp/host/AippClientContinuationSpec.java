package org.twelve.aipp.host;

import java.util.LinkedHashMap;
import java.util.Map;

/** Generic AIPP request for one Host-routed client tool followed by an owner callback. */
public final class AippClientContinuationSpec {
    public static final String FIELD = "client_continuation";
    public static final String TYPE = "host.client_tool/v1";
    public static final int SCHEMA_VERSION = 1;

    private AippClientContinuationSpec() {}

    public static Map<String, Object> request(String tool, Map<String, ?> args,
                                              String callbackTool, Map<String, ?> callbackArgs) {
        if (tool == null || tool.isBlank()) throw new IllegalArgumentException("client tool is required");
        if (callbackTool == null || callbackTool.isBlank()) {
            throw new IllegalArgumentException("callback tool is required");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", TYPE);
        out.put("schema_version", SCHEMA_VERSION);
        out.put("tool", tool.trim());
        out.put("args", args == null ? Map.of() : new LinkedHashMap<>(args));
        out.put("callback_tool", callbackTool.trim());
        out.put("callback_args", callbackArgs == null ? Map.of() : new LinkedHashMap<>(callbackArgs));
        return out;
    }
}
