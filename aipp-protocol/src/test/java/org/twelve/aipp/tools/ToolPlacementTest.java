package org.twelve.aipp.tools;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolPlacementTest {

    @Test
    void legacyNestedScope_isInert() {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "world_modify_decision");
        tool.put("visibility", List.of("llm"));
        tool.put("scope", Map.of(
                "level", "widget",
                "owner_app", "world",
                "owner_widget", "entity-graph",
                "visible_when", "canvas_open"));

        ToolPlacement.normalize(tool);

        assertThat(tool.get("owner_widget")).isNull();
        assertThat(ToolPlacement.ownerWidget(tool)).isNull();
        assertThat(ToolPlacement.isWidgetLlmTool(tool)).isFalse();
    }

    @Test
    void legacyUniversalLevel_noLongerGrantsRouterShortcut() {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "decision_list_view");
        tool.put("visibility", List.of("llm", "ui"));
        tool.put("scope", Map.of("level", "universal", "owner_app", "world", "visible_when", "always"));

        ToolPlacement.normalize(tool);

        assertThat(ToolPlacement.isRouterShortcut(tool)).isFalse();
        assertThat(ToolPlacement.llmDedupRank(tool)).isEqualTo(2);
    }

    @Test
    void flatV3Fields_declarePlacement() {
        Map<String, Object> widgetTool = new LinkedHashMap<>();
        widgetTool.put("name", "world_modify_decision");
        widgetTool.put("visibility", List.of("llm"));
        widgetTool.put("owner_widget", " entity-graph ");
        ToolPlacement.normalize(widgetTool);
        assertThat(ToolPlacement.ownerWidget(widgetTool)).isEqualTo("entity-graph");
        assertThat(ToolPlacement.isWidgetLlmTool(widgetTool)).isTrue();

        Map<String, Object> shortcut = new LinkedHashMap<>();
        shortcut.put("name", "decision_list_view");
        shortcut.put("visibility", List.of("llm", "ui"));
        shortcut.put("router_shortcut", true);
        assertThat(ToolPlacement.isRouterShortcut(shortcut)).isTrue();
        assertThat(ToolPlacement.llmDedupRank(shortcut)).isEqualTo(3);
    }

    @Test
    void refreshToolFromWidget_readsCanonicalFieldOnly() {
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("refresh_tool", "memory_view");
        assertThat(ToolPlacement.refreshToolFromWidget(w)).isEqualTo("memory_view");

        // v2.8: legacy refresh_skill 已移除，不再回退
        Map<String, Object> legacyOnly = new LinkedHashMap<>();
        legacyOnly.put("refresh_skill", "legacy");
        assertThat(ToolPlacement.refreshToolFromWidget(legacyOnly)).isNull();
    }

    @Test
    void bindWidget_setsMutatesDisplay() {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "memory_update");
        ToolPlacement.bindWidget(tool, "memory-manager", true);
        assertThat(ToolPlacement.ownerWidget(tool)).isEqualTo("memory-manager");
        assertThat(ToolPlacement.mutatesDisplay(tool)).isTrue();
    }

    @Test
    void widgetBoundRanksBelowAppWide() {
        Map<String, Object> app = Map.of("name", "x", "visibility", List.of("llm"));
        Map<String, Object> widget = new LinkedHashMap<>();
        widget.put("name", "x");
        widget.put("visibility", List.of("llm"));
        widget.put("owner_widget", "entity-graph");

        assertThat(ToolPlacement.llmDedupRank(app)).isGreaterThan(ToolPlacement.llmDedupRank(widget));
    }
}
