package org.twelve.aipp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AIPP Configuration 协议校验 — 配置元数据与值归 AIPP 所有，Host 仅代理 HTTP。
 *
 * <p>详见 {@code spec/configuration.md} 与 {@code README.md} §6.7。
 *
 * @see AippAppSpec#assertValidAppManifest(com.fasterxml.jackson.databind.JsonNode)
 */
public class AippConfigurationSpec {

    private static final Set<String> CONTAINER_TYPES = Set.of("group", "panel");
    private static final Set<String> CONTROL_TYPES = Set.of(
            "label", "input", "combobox", "list", "radiobox", "checkbox", "checklist", "rich_text"
    );
    private static final Set<String> INPUT_TYPES = Set.of("text", "number", "password");

    /**
     * 若 {@code /api/app} 含 {@code configuration}，校验其 {@code ui.layout} 树。
     * 无 {@code configuration} 时无操作。
     */
    public void assertValidConfigurationInAppManifest(JsonNode appManifest) {
        if (appManifest == null || !appManifest.has("configuration")) return;
        JsonNode cfg = appManifest.get("configuration");
        assertThat(cfg.isObject())
                .as("[AIPP Configuration] 'configuration' 必须是对象").isTrue();
        assertThat(cfg.has("ui"))
                .as("[AIPP Configuration] 'configuration' 必须包含 'ui'").isTrue();
        JsonNode ui = cfg.get("ui");
        assertThat(ui.isObject())
                .as("[AIPP Configuration] 'configuration.ui' 必须是对象").isTrue();
        assertThat(ui.has("layout"))
                .as("[AIPP Configuration] 'configuration.ui' 必须包含 'layout'").isTrue();
        if (cfg.has("methods")) {
            assertValidMethods(cfg.get("methods"), "configuration.methods");
        }
        assertValidLayoutNode(ui.get("layout"), "configuration.ui.layout");
    }

    /** 校验 {@code GET /api/configuration} 响应。 */
    public void assertValidConfigurationGetResponse(JsonNode response) {
        assertThat(response.has("ok"))
                .as("[AIPP Configuration] GET /api/configuration 缺少 'ok'").isTrue();
        assertThat(response.get("ok").asBoolean())
                .as("[AIPP Configuration] GET /api/configuration 期望 ok=true").isTrue();
        assertThat(response.has("values"))
                .as("[AIPP Configuration] GET /api/configuration 缺少 'values'").isTrue();
        assertThat(response.get("values").isObject())
                .as("[AIPP Configuration] 'values' 必须是 JSON 对象").isTrue();
    }

    /** 校验 {@code PUT /api/configuration} 请求体。 */
    public void assertValidConfigurationPutRequest(JsonNode body) {
        assertThat(body.has("values"))
                .as("[AIPP Configuration] PUT /api/configuration 请求体缺少 'values'").isTrue();
        assertThat(body.get("values").isObject())
                .as("[AIPP Configuration] PUT 请求 'values' 必须是 JSON 对象").isTrue();
    }

    /** 校验 {@code PUT /api/configuration} 成功响应。 */
    public void assertValidConfigurationPutResponse(JsonNode response) {
        assertThat(response.has("ok"))
                .as("[AIPP Configuration] PUT /api/configuration 响应缺少 'ok'").isTrue();
        assertThat(response.get("ok").asBoolean())
                .as("[AIPP Configuration] PUT 期望 ok=true").isTrue();
    }

    private void assertValidLayoutNode(JsonNode node, String path) {
        assertThat(node.isObject())
                .as("[AIPP Configuration] %s 必须是对象", path).isTrue();
        assertThat(node.has("type"))
                .as("[AIPP Configuration] %s 缺少 'type'", path).isTrue();
        String type = node.path("type").asText("").trim();
        assertThat(!type.isBlank())
                .as("[AIPP Configuration] %s 'type' 不能为空", path).isTrue();

        if (CONTAINER_TYPES.contains(type)) {
            assertContainer(node, path, type);
            return;
        }
        if (CONTROL_TYPES.contains(type)) {
            assertControl(node, path, type);
            return;
        }
        throw new AssertionError(
                "[AIPP Configuration] " + path + " 未知 type='" + type
                        + "'；容器=" + CONTAINER_TYPES + " 控件=" + CONTROL_TYPES);
    }

    private void assertContainer(JsonNode node, String path, String type) {
        if (node.has("width")) {
            String w = node.path("width").asText("");
            if (!"fill".equals(w)) {
                assertThat(node.get("width").isNumber() && node.get("width").asInt() > 0)
                        .as("[AIPP Configuration] %s width 应为 'fill' 或正整数", path)
                        .isTrue();
            }
        }
        if (node.has("layout_mode")) {
            assertThat(node.path("layout_mode").asText())
                    .as("[AIPP Configuration] %s layout_mode 目前仅支持 'stream'", path)
                    .isEqualTo("stream");
        }
        assertThat(node.has("children"))
                .as("[AIPP Configuration] %s（%s）缺少 'children'", path, type).isTrue();
        assertThat(node.get("children").isArray())
                .as("[AIPP Configuration] %s children 必须是数组", path).isTrue();
        assertThat(node.get("children").size())
                .as("[AIPP Configuration] %s children 至少 1 项", path).isGreaterThan(0);
        int i = 0;
        for (JsonNode child : node.get("children")) {
            assertValidLayoutNode(child, path + ".children[" + i + "]");
            i++;
        }
    }

    private void assertControl(JsonNode node, String path, String type) {
        if ("label".equals(type)) {
            assertThat(node.path("text").asText())
                    .as("[AIPP Configuration] %s label 缺少非空 'text'", path).isNotBlank();
            return;
        }
        assertThat(node.path("bind").asText())
                .as("[AIPP Configuration] %s 缺少非空 'bind'", path).isNotBlank();
        if ("input".equals(type) && node.has("input_type")) {
            assertThat(INPUT_TYPES)
                    .as("[AIPP Configuration] %s input_type 非法", path)
                    .contains(node.path("input_type").asText());
        }
        if ("combobox".equals(type) || "radiobox".equals(type)) {
            assertValidOptions(node, path);
        }
        if ("checklist".equals(type)) {
            assertValidOptionsArrayAllowEmpty(node, path);
            if (node.has("options_refresh")) {
                assertValidOptionsRefresh(node.get("options_refresh"), path + ".options_refresh");
            }
        }
    }

    private void assertValidOptions(JsonNode node, String path) {
        assertThat(node.has("options"))
                .as("[AIPP Configuration] %s 缺少 'options'", path).isTrue();
        assertThat(node.get("options").isArray())
                .as("[AIPP Configuration] %s options 必须是数组", path).isTrue();
        assertThat(node.get("options").size())
                .as("[AIPP Configuration] %s options 至少 1 项", path).isGreaterThan(0);
        for (JsonNode opt : node.get("options")) {
            assertThat(opt.path("value").asText())
                    .as("[AIPP Configuration] %s options[].value 不能为空", path).isNotBlank();
            assertThat(opt.path("label").asText())
                    .as("[AIPP Configuration] %s options[].label 不能为空", path).isNotBlank();
        }
    }

    private void assertValidOptionsArrayAllowEmpty(JsonNode node, String path) {
        assertThat(node.has("options"))
                .as("[AIPP Configuration] %s 缺少 'options'", path).isTrue();
        assertThat(node.get("options").isArray())
                .as("[AIPP Configuration] %s options 必须是数组", path).isTrue();
        for (JsonNode opt : node.get("options")) {
            assertThat(opt.path("value").asText())
                    .as("[AIPP Configuration] %s options[].value 不能为空", path).isNotBlank();
            assertThat(opt.path("label").asText())
                    .as("[AIPP Configuration] %s options[].label 不能为空", path).isNotBlank();
        }
    }

    private void assertValidMethods(JsonNode methods, String path) {
        assertThat(methods.isObject())
                .as("[AIPP Configuration] %s 必须是对象", path).isTrue();
        if (methods.has("load")) {
            assertValidMethodNode(methods.get("load"), path + ".load");
        }
        if (methods.has("refresh")) {
            assertValidMethodNode(methods.get("refresh"), path + ".refresh");
        }
    }

    private void assertValidMethodNode(JsonNode method, String path) {
        assertThat(method.isObject())
                .as("[AIPP Configuration] %s 必须是对象", path).isTrue();
        assertThat(method.path("endpoint").asText())
                .as("[AIPP Configuration] %s endpoint 不能为空", path).isNotBlank();
    }

    private void assertValidOptionsRefresh(JsonNode refresh, String path) {
        assertThat(refresh.isObject())
                .as("[AIPP Configuration] %s 必须是对象", path).isTrue();
        assertThat(refresh.path("endpoint").asText())
                .as("[AIPP Configuration] %s endpoint 不能为空", path).isNotBlank();
        assertThat(refresh.path("when_bind").asText())
                .as("[AIPP Configuration] %s when_bind 不能为空", path).isNotBlank();
    }
}
