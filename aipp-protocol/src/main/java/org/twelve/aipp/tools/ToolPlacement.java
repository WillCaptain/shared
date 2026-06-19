package org.twelve.aipp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Tool placement + widget refresh semantics for {@code GET /api/tools} and {@code GET /api/widgets}.
 *
 * <p><b>For coding agents</b> — read {@code spec/field-semantics.md} in aipp-protocol first.
 * The fields below are <em>orthogonal</em>; do not conflate them:
 *
 * <table>
 *   <tr><th>Field(s)</th><th>Question answered</th><th>Declared on</th></tr>
 *   <tr><td>{@code visibility}, {@code owner_widget}, {@code router_shortcut}</td>
 *       <td>Who may call? Main chat vs canvas vs Host scheduler?</td><td>tool</td></tr>
 *   <tr><td>{@code mutates_display}</td>
 *       <td>May this call stale the open widget's rendered data?</td><td>tool</td></tr>
 *   <tr><td>{@code refresh_tool}</td>
 *       <td>Which tool reloads widget data after a side effect?</td><td>widget</td></tr>
 * </table>
 *
 * <h2>Canonical tool shape (v3+)</h2>
 * <pre>{@code
 * {
 *   "name": "memory_update",
 *   "visibility": ["llm", "ui"],
 *   "owner_widget": "memory-manager",   // optional — canvas-bound; excluded from main-chat LLM catalog
 *   "router_shortcut": true,            // optional — Router one-hop in root session (not with owner_widget)
 *   "mutates_display": true             // optional — write tool; Host may auto-call widget refresh_tool
 * }
 * }</pre>
 *
 * <h2>Widget refresh (v2.7)</h2>
 * <p>Do <b>not</b> list write tools on the widget as {@code mutating_tools}. Instead mark each
 * write tool with {@code mutates_display: true} (same {@code owner_widget}). Widget declares
 * {@code refresh_tool}.
 *
 * <p>Legacy nested {@code scope} ({@code level}, {@code owner_widget}, {@code visible_when}) is
 * <b>no longer read</b> (removed 2026-06): apps must emit the flat v3 fields above. A nested
 * {@code scope} on ingest is inert and stripped before any LLM sees the tool.
 *
 * @see org.twelve.aipp.tools.package-info
 */
public final class ToolPlacement {

    private ToolPlacement() {}

    /**
     * Normalize flat v3 placement fields in place (currently: trims {@code owner_widget}).
     *
     * <p>Legacy nested {@code scope} is intentionally <b>not</b> lifted anymore — placement
     * must be declared with flat fields ({@code owner_widget}, {@code router_shortcut}).
     *
     * <p>Does <b>not</b> infer {@code mutates_display} from legacy widget {@code mutating_tools};
     * side effects must be declared on each tool in {@code /api/tools}.
     */
    public static void normalize(Map<String, Object> tool) {
        if (tool == null) return;
        if (hasText(tool.get("owner_widget"))) {
            tool.put("owner_widget", tool.get("owner_widget").toString().trim());
        }
    }

    /**
     * Widget type this tool is bound to ({@code owner_widget}), or {@code null} if app-wide.
     *
     * <p>App-wide tools (no owner) appear in main-chat LLM catalog; widget-bound LLM tools are
     * merged only when that canvas is active.
     */
    public static String ownerWidget(Map<String, Object> tool) {
        if (tool == null) return null;
        Object top = tool.get("owner_widget");
        return hasText(top) ? top.toString().trim() : null;
    }

    /** @see #ownerWidget(Map) */
    public static boolean hasOwnerWidget(Map<String, Object> tool) {
        return ownerWidget(tool) != null;
    }

    /**
     * Whether Router may one-hop this tool from root main session without a skill playbook.
     *
     * <p>Declared as {@code router_shortcut: true}. Use for list/open entry tools
     * ({@code recipe_list}, {@code world_list_view}), not for widget-bound writes.
     */
    public static boolean isRouterShortcut(Map<String, Object> tool) {
        return tool != null && Boolean.TRUE.equals(tool.get("router_shortcut"));
    }

    /**
     * LLM-visible tool tied to a widget — excluded from main-chat catalog, merged when canvas opens.
     *
     * <p>Host: {@code ToolCatalog.toolsForLlm()} skips these; canvas merge adds them back
     * via {@link #stripPlacementForLlm(Map)}.
     */
    public static boolean isWidgetLlmTool(Map<String, Object> tool) {
        return hasOwnerWidget(tool) && visibilityContains(tool, "llm");
    }

    /**
     * Widget internal UI tool — {@code visibility} is only {@code ui} (Property Panel, buttons).
     *
     * <p>Typically {@code owner_widget} is set; no {@code mutates_display} unless the UI action
     * changes rendered data (then set {@code mutates_display} for Host auto-refresh).
     */
    public static boolean isWidgetUiOnly(Map<String, Object> tool) {
        return hasOwnerWidget(tool) && isUiOnlyVisibility(tool.get("visibility"));
    }

    /** True when tool's {@code owner_widget} equals the given widget type. */
    public static boolean matchesWidget(Map<String, Object> tool, String widgetType) {
        if (widgetType == null || widgetType.isBlank()) return false;
        String owner = ownerWidget(tool);
        return owner != null && Objects.equals(owner, widgetType);
    }

    /**
     * Whether calling this tool may stale the widget canvas display.
     *
     * <p><b>Not</b> the same as {@code owner_widget}: a widget-bound read tool
     * ({@code memory_query}) has {@code owner_widget} but should <em>omit</em> this flag.
     * Write tools ({@code memory_update}) set {@code mutates_display: true}.
     *
     * <p>Host uses this (with active canvas + widget {@code refresh_tool}) to decide
     * auto-refresh after an LLM turn. Declared on {@code /api/tools}, not widget {@code mutating_tools}.
     */
    public static boolean mutatesDisplay(Map<String, Object> tool) {
        return tool != null && Boolean.TRUE.equals(tool.get("mutates_display"));
    }

    /**
     * Convenience for app {@code mergeWidgetScope} helpers: set {@code owner_widget} and optional
     * {@code mutates_display}, then {@link #normalize(Map)}.
     *
     * @param mutatesDisplay {@code true} for create/update/delete/link tools that stale the canvas
     */
    public static void bindWidget(Map<String, Object> tool, String widgetType, boolean mutatesDisplay) {
        if (tool == null || widgetType == null || widgetType.isBlank()) return;
        tool.put("owner_widget", widgetType.trim());
        if (mutatesDisplay) {
            tool.put("mutates_display", true);
        } else {
            tool.remove("mutates_display");
        }
        normalize(tool);
    }

    /**
     * Resolve widget reload tool from manifest: {@code refresh_tool}
     * (v2.8: legacy {@code refresh_skill} removed).
     *
     * <p>Distinct from {@code entry_tool}: entry opens the widget; refresh reloads data for an
     * already-open canvas. Often the same tool name (e.g. {@code memory_view}) but different jobs.
     */
    public static String refreshToolFromWidget(Map<String, Object> widget) {
        if (widget == null) return null;
        Object rt = widget.get("refresh_tool");
        return hasText(rt) ? rt.toString().trim() : null;
    }

    /**
     * Substitute the {@code {refresh_tool}} view-hint placeholder after
     * {@link #refreshToolFromWidget(Map)} so hints stay DRY.
     */
    public static String substituteRefreshPlaceholder(String hint, String refreshTool) {
        if (hint == null || refreshTool == null || refreshTool.isBlank()) return hint;
        return hint.replace("{refresh_tool}", refreshTool);
    }

    /**
     * Dedup rank when two LLM tools share a name: higher wins.
     *
     * <p>Order: router shortcut (3) &gt; plain app-wide (2) &gt; widget-bound (1).
     */
    public static int llmDedupRank(Map<String, Object> tool) {
        if (tool == null) return 0;
        if (hasOwnerWidget(tool)) return 1;
        if (isRouterShortcut(tool)) return 3;
        return 2;
    }

    /**
     * Copy tool for canvas LLM merge — strip placement metadata the model must not see.
     *
     * <p>Removes {@code visibility}, {@code scope}, {@code owner_widget}, {@code router_shortcut},
     * and {@code mutates_display} (side-effect hints are for Host/ui_hints, not function schema).
     */
    public static Map<String, Object> stripPlacementForLlm(Map<String, Object> tool) {
        Map<String, Object> out = new LinkedHashMap<>(tool);
        out.remove("visibility");
        out.remove("scope");
        out.remove("owner_widget");
        out.remove("router_shortcut");
        out.remove("mutates_display");
        return out;
    }

    /** Whether {@code visibility} array contains a token ({@code llm}, {@code ui}, {@code host}). */
    public static boolean visibilityContains(Map<String, Object> tool, String token) {
        Object v = tool == null ? null : tool.get("visibility");
        if (!(v instanceof List<?> list)) {
            return "llm".equals(token);
        }
        for (Object o : list) {
            if (token.equals(String.valueOf(o))) return true;
        }
        return false;
    }

    private static boolean isUiOnlyVisibility(Object visibility) {
        if (!(visibility instanceof List<?> list) || list.isEmpty()) return false;
        for (Object o : list) {
            if (!"ui".equals(String.valueOf(o))) return false;
        }
        return true;
    }

    private static boolean hasText(Object v) {
        return v != null && !v.toString().isBlank();
    }
}
