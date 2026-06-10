package org.twelve.aipp.invoke;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared entry-boundary filter for Decision Reactor invoke protocol.
 *
 * <p>Catalog classification uses activator shape only ({@code manualEnabled},
 * {@code activator.type}). Whether a pushed session notifies listeners uses runtime
 * {@code payload.entry_activation} and {@code payload.event_type} on
 * {@code OntologySessionChangeEvent} (see decision-reactor AIPP docs).
 *
 * @see spec/ontology-world-catalog.md
 */
public final class DecisionReactorEntryTemplates {

    public static final String ENTRY_MANUAL = "manual";
    public static final String ENTRY_SCHEDULE = "schedule";
    public static final String ENTRY_ONTOLOGY = "ontology";

    /** @deprecated use {@link #ENTRY_ONTOLOGY}; kept for binary compatibility. */
    @Deprecated
    public static final String ENTRY_EXTERNAL_ONTOLOGY = ENTRY_ONTOLOGY;

    private DecisionReactorEntryTemplates() {}

    /** True when this template is a reactor entry boundary (registerable). */
    public static boolean isReactorEligible(JsonNode decision) {
        return classify(decision) != null;
    }

    public static boolean isEntryTemplate(JsonNode decision) {
        return classify(decision) != null;
    }

    /**
     * Entry type from template activator, or null when not an entry boundary
     * (e.g. {@code activator.type == decision} chain steps).
     */
    public static String classify(JsonNode decision) {
        if (decision == null || decision.isMissingNode()) return null;
        if (manualEnabled(decision)) return ENTRY_MANUAL;
        JsonNode activator = decision.path("activator");
        String type = activator.path("type").asText("").trim().toLowerCase(Locale.ROOT);
        if ("schedule".equals(type)) return ENTRY_SCHEDULE;
        if ("ontology".equals(type)) return ENTRY_ONTOLOGY;
        return null;
    }

    /** @deprecated use {@link #classify}; display and eligible types are the same. */
    @Deprecated
    public static String classifyForDisplay(JsonNode decision) {
        return classify(decision);
    }

    public static Map<String, Object> toEntryTemplateRow(JsonNode decision) {
        String entryType = classify(decision);
        if (entryType == null) return null;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("template_id", decision.path("id").asText("").trim());
        row.put("goal", text(decision.path("intent"), "goal"));
        row.put("context", text(decision.path("intent"), "context"));
        row.put("entry_type", entryType);
        row.put("manual_enabled", manualEnabled(decision));
        JsonNode activator = decision.path("activator");
        if (activator.isObject() && !activator.isEmpty()) {
            row.put("activator", jsonToMap(activator));
        }
        return row;
    }

    static boolean manualEnabled(JsonNode root) {
        if (root.has("manualEnabled")) return root.path("manualEnabled").asBoolean(false);
        if (root.has("manual_enabled")) return root.path("manual_enabled").asBoolean(false);
        return false;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) return "";
        return node.path(field).asText("").trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> jsonToMap(JsonNode node) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().convertValue(node, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    public static List<Map<String, Object>> filterEntryRows(JsonNode decisionsArray) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (decisionsArray == null || !decisionsArray.isArray()) return rows;
        for (JsonNode d : decisionsArray) {
            Map<String, Object> row = toEntryTemplateRow(d);
            if (row != null && !String.valueOf(row.get("template_id")).isBlank()) {
                rows.add(row);
            }
        }
        rows.sort((a, b) -> String.valueOf(a.get("template_id"))
                .compareToIgnoreCase(String.valueOf(b.get("template_id"))));
        return rows;
    }
}
