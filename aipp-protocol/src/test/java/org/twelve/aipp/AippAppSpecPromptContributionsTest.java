package org.twelve.aipp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code prompt_contributions} structure + ambient_prompt budget gate
 * (host-decoupling §6 / §6.1).
 */
@DisplayName("AippAppSpec — prompt_contributions budget")
class AippAppSpecPromptContributionsTest {

    private AippAppSpec spec;
    private ObjectMapper json;

    @BeforeEach
    void setUp() {
        spec = new AippAppSpec();
        json = new ObjectMapper();
    }

    private ObjectNode toolsRootWithAmbient(String id, int contentLen) {
        ObjectNode root = json.createObjectNode();
        ArrayNode pc = root.putArray("prompt_contributions");
        ObjectNode c = pc.addObject();
        c.put("id", id);
        c.put("layer", "ambient_prompt");
        c.put("priority", 30);
        c.put("content", "x".repeat(contentLen));
        return root;
    }

    @Test
    @DisplayName("精简的 ambient_prompt 通过")
    void shortAmbient_passes() {
        assertThatCode(() -> spec.assertValidPromptContributions(toolsRootWithAmbient("ok", 228)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("行为型策略（接近上限）仍通过")
    void behavioralPolicyUnderCeiling_passes() {
        assertThatCode(() -> spec.assertValidPromptContributions(toolsRootWithAmbient("memory", 1551)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("超出绝对上限的 ambient_prompt 失败")
    void oversizeAmbient_fails() {
        ObjectNode root = toolsRootWithAmbient("bloated", AippAppSpec.MAX_AMBIENT_PROMPT_CHARS + 1);
        assertThatThrownBy(() -> spec.assertValidPromptContributions(root))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("预算上限");
    }

    @Test
    @DisplayName("非法 layer 失败")
    void invalidLayer_fails() {
        ObjectNode root = json.createObjectNode();
        ObjectNode c = root.putArray("prompt_contributions").addObject();
        c.put("id", "x").put("layer", "not_a_layer").put("content", "hi");
        assertThatThrownBy(() -> spec.assertValidPromptContributions(root))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("空 content 失败")
    void blankContent_fails() {
        ObjectNode root = json.createObjectNode();
        ObjectNode c = root.putArray("prompt_contributions").addObject();
        c.put("id", "x").put("layer", "ambient_prompt").put("content", "   ");
        assertThatThrownBy(() -> spec.assertValidPromptContributions(root))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("无 prompt_contributions 时跳过")
    void absent_passes() {
        assertThatCode(() -> spec.assertValidPromptContributions(json.createObjectNode()))
                .doesNotThrowAnyException();
    }
}
