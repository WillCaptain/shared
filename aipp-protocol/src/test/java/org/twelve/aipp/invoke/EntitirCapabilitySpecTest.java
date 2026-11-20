package org.twelve.aipp.invoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntitirCapabilitySpecTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void namesAreUniqueNamespacedAndCoveredByTheBundles() {
        assertThat(EntitirTools.ALL).hasSize(26).doesNotHaveDuplicates();
        assertThat(EntitirTools.ALL).allSatisfy(name ->
                assertThat(name).startsWith(EntitirTools.PREFIX).matches("[a-z][a-z0-9_]*"));
        assertThat(new HashSet<>(EntitirTools.requires(
                EntitirTools.WORLD_REQUIRES, EntitirTools.MODEL_REQUIRES,
                EntitirTools.DATA_READ_REQUIRES, EntitirTools.DATA_WRITE_REQUIRES,
                EntitirTools.CAPABILITY_REQUIRES, EntitirTools.RUN_REQUIRES,
                EntitirTools.TOPIC_REQUIRES)))
                .isEqualTo(new HashSet<>(EntitirTools.ALL));
        assertThat(EntitirTools.ALL).containsAll(EntitirTools.MUTATING);
        assertThat(EntitirTools.ALL).containsAll(EntitirTools.WITHOUT_WORLD_ID);
    }

    @Test
    void requiresMergesBundlesWithoutRepeatingASharedName() {
        assertThat(EntitirTools.requires(EntitirTools.CAPABILITY_REQUIRES, EntitirTools.RUN_REQUIRES))
                .doesNotHaveDuplicates()
                .containsOnlyOnce(EntitirTools.CAPABILITY_LIST)
                .contains(EntitirTools.DECISION_RUN, EntitirTools.CAPABILITY_SAVE);
    }

    @Test
    void earlierNamesStayOutsideTheContractNamespace() {
        assertThat(EntitirTools.ALL).containsAll(EntitirTools.EARLIER_NAMES.keySet());
        assertThat(EntitirTools.EARLIER_NAMES.values())
                .doesNotHaveDuplicates()
                .noneMatch(EntitirTools.ALL::contains);
        assertThat(EntitirTools.ALL)
                .doesNotContainAnyElementsOf(OntologyWorldTools.WIKI_REQUIRES)
                .doesNotContainAnyElementsOf(OntologyWorldTools.CATALOG_REQUIRES);
    }

    @Test
    void acceptsAHeadlessWorldKeyedManifest() {
        ObjectNode root = manifest(
                tool(EntitirTools.WORLD_LIST, "none", List.of()),
                tool(EntitirTools.WORLD_CREATE, "mutating", List.of("name")),
                tool(EntitirTools.DATA_EVAL, "none", List.of("world_id", "expression")),
                tool(EntitirTools.DATA_CHANGE, "mutating", List.of("world_id", "changes")),
                tool(EntitirTools.DECISION_RUN, "idempotent", List.of("world_id", "template_id")));

        EntitirCapabilitySpec.assertValidProviderTools(root);
        EntitirCapabilitySpec.assertProvides(root, List.of(EntitirTools.DATA_EVAL, EntitirTools.DATA_CHANGE));
        assertThat(EntitirCapabilitySpec.missing(root))
                .hasSize(21)
                .contains(EntitirTools.TOPIC_PUBLISH)
                .doesNotContain(EntitirTools.DATA_EVAL);
    }

    @Test
    void ignoresToolsOutsideTheContractEvenUnderTheSamePrefix() {
        ObjectNode designerTool = tool("entitir_guide", "", List.of("session_id"));
        designerTool.put("owner_widget", "entity-graph");

        EntitirCapabilitySpec.assertValidProviderTools(manifest(designerTool));
    }

    @Test
    void rejectsACanvasBoundContractTool() {
        ObjectNode bound = tool(EntitirTools.TOPIC_SAVE, "mutating", List.of("world_id", "title"));
        bound.put("owner_widget", "entity-graph");

        assertThatThrownBy(() -> EntitirCapabilitySpec.assertValidProviderTools(manifest(bound)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("owner_widget");
    }

    @Test
    void rejectsASessionKeyedContractTool() {
        ObjectNode keyed = tool(EntitirTools.WORLD_BUILD, "mutating", List.of("world_id"));
        ((ObjectNode) keyed.path("parameters").path("properties")).putObject("session_id").put("type", "string");

        assertThatThrownBy(() -> EntitirCapabilitySpec.assertValidProviderTools(manifest(keyed)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("session_id");
    }

    @Test
    void rejectsAWorldScopedToolThatDoesNotRequireWorldId() {
        ObjectNode loose = tool(EntitirTools.DATA_SCHEMA, "none", List.of());

        assertThatThrownBy(() -> EntitirCapabilitySpec.assertValidProviderTools(manifest(loose)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("world_id");
    }

    @Test
    void rejectsAToolHiddenFromTheModel() {
        ObjectNode hidden = tool(EntitirTools.DATA_ASK, "none", List.of("world_id", "question"));
        hidden.putArray("visibility").add("host");

        assertThatThrownBy(() -> EntitirCapabilitySpec.assertValidProviderTools(manifest(hidden)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("llm");
    }

    @Test
    void rejectsAWriteDeclaredAsReadAndAReadDeclaredAsWrite() {
        ObjectNode write = tool(EntitirTools.WORLD_DELETE, "none", List.of("world_id"));
        assertThatThrownBy(() -> EntitirCapabilitySpec.assertValidProviderTools(manifest(write)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("writes");

        ObjectNode read = tool(EntitirTools.EXECUTION_GET, "mutating", List.of("execution_id"));
        assertThatThrownBy(() -> EntitirCapabilitySpec.assertValidProviderTools(manifest(read)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("only reads");
    }

    @Test
    void reportsAMissingBundleMember() {
        JsonNode root = manifest(tool(EntitirTools.TOPIC_LIST, "none", List.of("world_id")));

        assertThatThrownBy(() -> EntitirCapabilitySpec.assertProvides(root, EntitirTools.TOPIC_REQUIRES))
                .isInstanceOf(AssertionError.class);
    }

    private static ObjectNode manifest(ObjectNode... tools) {
        ObjectNode root = JSON.createObjectNode();
        root.put("app", "provider");
        root.put("version", "1.0");
        ArrayNode list = root.putArray("tools");
        for (ObjectNode tool : tools) list.add(tool);
        return root;
    }

    private static ObjectNode tool(String name, String sideEffect, List<String> required) {
        ObjectNode tool = JSON.createObjectNode();
        tool.put("name", name);
        tool.put("description", "test tool");
        tool.putArray("visibility").add("llm").add("ui");
        if (!sideEffect.isEmpty()) tool.put("side_effect", sideEffect);
        ObjectNode parameters = tool.putObject("parameters");
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        ArrayNode requiredNode = parameters.putArray("required");
        for (String field : required) {
            properties.putObject(field).put("type", "string");
            requiredNode.add(field);
        }
        return tool;
    }
}
