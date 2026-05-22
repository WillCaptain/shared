package org.twelve.shared.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Shared Host event protocol between apps and the world-one host.
 *
 * <p>This module deliberately has no dependency on world-one or world-entitir.
 * Apps publish these events to a host endpoint; the host stores and renders them.
 */
public record HostEvent(
        String id,
        String type,
        String status,
        String worldId,
        String scopeId,
        String uiSessionId,
        Map<String, Object> source,
        Map<String, Object> businessData,
        Map<String, Object> tags,
        Map<String, Object> widget
) {
    public static final String TYPE_PARAMETER_MISSING = "parameter_missing";
    public static final String STATUS_PENDING = "pending";
    public static final String DEFAULT_RESUME_TOOL = "manual_decision_execute";

    public static HostEvent parameterMissing(String worldId,
                                             String templateId,
                                             String scopeId,
                                             String uiSessionId,
                                             List<String> missingParams,
                                             Map<String, Object> widget,
                                             String resumeTool,
                                             Map<String, Object> resumeArgs,
                                             Map<String, Object> providedParams,
                                             List<Map<String, Object>> chainSnapshot) {
        Map<String, Object> source = Map.of("kind", "decision", "id", blankToEmpty(templateId));
        Map<String, Object> businessData = new LinkedHashMap<>();
        businessData.put("missing_parameters", missingParams == null ? List.of() : missingParams);
        businessData.put("resume_tool", isBlank(resumeTool) ? DEFAULT_RESUME_TOOL : resumeTool);
        businessData.put("resume_args", resumeArgs == null ? Map.of() : resumeArgs);
        businessData.put("provided_parameters", providedParams == null ? Map.of() : providedParams);
        if (chainSnapshot != null && !chainSnapshot.isEmpty()) {
            businessData.put("chain_snapshot", chainSnapshot);
        }

        Map<String, Object> tags = new LinkedHashMap<>();
        if (!isBlank(templateId)) tags.put("decision", templateId);
        if (!isBlank(worldId)) tags.put("world_id", worldId);

        return new HostEvent(
                deterministicParameterMissingId(worldId, scopeId, templateId),
                TYPE_PARAMETER_MISSING,
                STATUS_PENDING,
                blankToEmpty(worldId),
                blankToEmpty(scopeId),
                blankToEmpty(uiSessionId),
                source,
                businessData,
                tags,
                widget == null ? Map.of() : widget);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!isBlank(id)) out.put("id", id);
        out.put("type", blankToDefault(type, TYPE_PARAMETER_MISSING));
        out.put("status", blankToDefault(status, STATUS_PENDING));
        if (!isBlank(worldId)) out.put("world_id", worldId);
        if (!isBlank(scopeId)) out.put("scope_id", scopeId);
        if (!isBlank(uiSessionId)) out.put("ui_session_id", uiSessionId);
        out.put("source", source == null ? Map.of() : source);
        out.put("business_data", businessData == null ? Map.of() : businessData);
        out.put("tags", tags == null ? Map.of() : tags);
        out.put("widget", widget == null ? Map.of() : widget);
        return out;
    }

    @SuppressWarnings("unchecked")
    public static HostEvent fromMap(Map<String, Object> raw) {
        Map<String, Object> m = raw == null ? Map.of() : raw;
        return new HostEvent(
                str(m.get("id")),
                str(m.get("type")),
                str(m.get("status")),
                str(m.get("world_id")),
                str(m.get("scope_id")),
                str(m.get("ui_session_id")),
                m.get("source") instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of(),
                m.get("business_data") instanceof Map<?, ?> business ? (Map<String, Object>) business : Map.of(),
                m.get("tags") instanceof Map<?, ?> tags ? (Map<String, Object>) tags : Map.of(),
                m.get("widget") instanceof Map<?, ?> widget ? (Map<String, Object>) widget : Map.of());
    }

    public static String deterministicParameterMissingId(String worldId, String scopeId, String templateId) {
        String raw = blankToEmpty(worldId) + "|" + blankToEmpty(scopeId) + "|" + blankToEmpty(templateId);
        String readable = sanitize(worldId) + "_" + sanitize(templateId);
        if (readable.length() > 32) readable = readable.substring(0, 32);
        return "evt_pm_" + readable + "_" + shortHash(raw);
    }

    private static String sanitize(String raw) {
        String s = blankToEmpty(raw).replaceAll("[^A-Za-z0-9]+", "_");
        s = s.replaceAll("^_+", "").replaceAll("_+$", "");
        if (s.isBlank()) return "unknown";
        return s.length() <= 60 ? s : s.substring(0, 60);
    }

    private static String blankToDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String shortHash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                out.append(String.format("%02x", digest[i]));
            }
            return out.toString();
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }
}
