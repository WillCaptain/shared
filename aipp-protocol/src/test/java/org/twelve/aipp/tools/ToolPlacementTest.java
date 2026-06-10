package org.twelve.aipp.tools;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolPlacementTest {

    @Test
    void normalize_liftsLegacyScope() {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "world_modify_decision");
        tool.put("visibility", List.of("llm"));
        tool.put("scope", Map.of(
                "level", "widget",
                "owner_app", "world",
                "owner_widget", "entity-graph",
                "visible_when", "canvas_open"));

        ToolPlacement.normalize(tool);

        assertThat(tool.get("owner_widget")).isEqualTo("entity-graph");
        assertThat(ToolPlacement.ownerWidget(tool)).isEqualTo("entity-graph");
        assertThat(ToolPlacement.isWidgetLlmTool(tool)).isTrue();
    }

    @Test
    void normalize_liftsRouterShortcutFromUniversalLevel() {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "decision_list_view");
        tool.put("visibility", List.of("llm", "ui"));
        tool.put("scope", Map.of("level", "universal", "owner_app", "world", "visible_when", "always"));

        ToolPlacement.normalize(tool);

        assertThat(ToolPlacement.isRouterShortcut(tool)).isTrue();
        assertThat(ToolPlacement.llmDedupRank(tool)).isEqualTo(3);
    }

    @Test
    void refreshToolFromWidget_prefersCanonicalField() {
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("refresh_tool", "memory_view");
        w.put("refresh_skill", "legacy");
        assertThat(ToolPlacement.refreshToolFromWidget(w)).isEqualTo("memory_view");
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
