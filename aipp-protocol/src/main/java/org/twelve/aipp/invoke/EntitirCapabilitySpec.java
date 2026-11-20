package org.twelve.aipp.invoke;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provider-side gate for the Entitir capability ({@code spec/entitir-capability.md}).
 * Run it on the provider's {@code GET /api/tools} response. It checks only the
 * contract names a manifest declares; other tools in the same manifest are ignored.
 */
public final class EntitirCapabilitySpec {

    private EntitirCapabilitySpec() {}

    /**
     * Every contract tool the manifest declares is headless and keyed by world:
     * no {@code owner_widget}, callable by the model, no {@code session_id}
     * argument, {@code world_id} required where the contract has one, and a
     * {@code side_effect} that matches whether the tool writes.
     */
    public static void assertValidProviderTools(JsonNode toolsRoot) {
        for (Map.Entry<String, JsonNode> entry : contractTools(toolsRoot).entrySet()) {
            String name = entry.getKey();
            JsonNode tool = entry.getValue();

            assertThat(tool.path("owner_widget").asText(""))
                    .as("[Entitir capability] '%s' must not set owner_widget: another app's session has no such canvas",
                            name)
                    .isEmpty();
            assertThat(texts(tool.path("visibility")))
                    .as("[Entitir capability] '%s' visibility must include 'llm' so a consumer's model can call it",
                            name)
                    .contains("llm");

            JsonNode parameters = tool.path("parameters");
            assertThat(parameters.path("properties").has("session_id"))
                    .as("[Entitir capability] '%s' must not take session_id: the contract addresses a world by world_id",
                            name)
                    .isFalse();
            if (!EntitirTools.WITHOUT_WORLD_ID.contains(name)) {
                assertThat(texts(parameters.path("required")))
                        .as("[Entitir capability] '%s' must require world_id", name)
                        .contains("world_id");
            }

            String sideEffect = tool.path("side_effect").asText("");
            if (EntitirTools.MUTATING.contains(name)) {
                assertThat(sideEffect)
                        .as("[Entitir capability] '%s' writes, so side_effect must be 'idempotent' or 'mutating'", name)
                        .isIn("idempotent", "mutating");
            } else {
                assertThat(sideEffect)
                        .as("[Entitir capability] '%s' only reads, so side_effect must be 'none'", name)
                        .isEqualTo("none");
            }
        }
    }

    /** The manifest declares every one of {@code names}; use it for the bundles a provider claims to serve. */
    public static void assertProvides(JsonNode toolsRoot, Collection<String> names) {
        assertThat(contractTools(toolsRoot).keySet())
                .as("[Entitir capability] the provider manifest must declare these contract tools")
                .containsAll(names);
    }

    /** Contract names the manifest does not declare yet, in spec order. */
    public static List<String> missing(JsonNode toolsRoot) {
        Map<String, JsonNode> declared = contractTools(toolsRoot);
        List<String> missing = new ArrayList<>();
        for (String name : EntitirTools.ALL) {
            if (!declared.containsKey(name)) missing.add(name);
        }
        return missing;
    }

    private static Map<String, JsonNode> contractTools(JsonNode toolsRoot) {
        Map<String, JsonNode> tools = new LinkedHashMap<>();
        JsonNode list = toolsRoot == null ? null : toolsRoot.path("tools");
        if (list == null || !list.isArray()) return tools;
        for (JsonNode tool : list) {
            String name = tool.path("name").asText("");
            if (EntitirTools.ALL.contains(name)) tools.put(name, tool);
        }
        return tools;
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode value : array) values.add(value.asText(""));
        }
        return values;
    }
}
