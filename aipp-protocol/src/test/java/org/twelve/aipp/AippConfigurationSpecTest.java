package org.twelve.aipp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AIPP Configuration 协议测试")
class AippConfigurationSpecTest {

    private AippConfigurationSpec spec;
    private AippAppSpec appSpec;
    private ObjectMapper json;

    @BeforeEach
    void setUp() {
        spec = new AippConfigurationSpec();
        appSpec = new AippAppSpec();
        json = new ObjectMapper();
    }

    @Test
    @DisplayName("无 configuration 块时 manifest 仍合法")
    void manifestWithoutConfiguration_passes() {
        assertThatCode(() -> appSpec.assertValidAppManifest(baseManifest()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("合法 configuration.ui.layout 通过")
    void validLayout_passes() {
        ObjectNode m = baseManifest();
        m.set("configuration", validConfigurationBlock());
        assertThatCode(() -> appSpec.assertValidAppManifest(m))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("控件缺少 bind 时应失败")
    void controlMissingBind_fails() {
        ObjectNode m = baseManifest();
        ObjectNode cfg = validConfigurationBlock();
        ObjectNode layout = (ObjectNode) cfg.path("ui").path("layout");
        ArrayNode children = (ArrayNode) layout.path("children").get(0).path("children");
        ObjectNode input = (ObjectNode) children.get(0);
        input.remove("bind");
        m.set("configuration", cfg);
        assertThatThrownBy(() -> appSpec.assertValidAppManifest(m))
                .hasMessageContaining("bind");
    }

    @Test
    @DisplayName("GET /api/configuration 合法响应")
    void validGetResponse_passes() throws Exception {
        var node = json.readTree("{\"ok\":true,\"values\":{\"a\":1}}");
        assertThatCode(() -> spec.assertValidConfigurationGetResponse(node))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("PUT 请求体缺少 values 时应失败")
    void putMissingValues_fails() throws Exception {
        var node = json.readTree("{\"ok\":true}");
        assertThatThrownBy(() -> spec.assertValidConfigurationPutRequest(node))
                .hasMessageContaining("values");
    }

    private ObjectNode baseManifest() {
        ObjectNode m = json.createObjectNode();
        m.put("app_id", "demo-app");
        m.put("app_name", "Demo");
        m.put("app_icon", "<svg/>");
        m.put("app_description", "demo");
        m.put("app_color", "#000");
        m.put("is_active", true);
        m.put("version", "1.0");
        return m;
    }

    private ObjectNode validConfigurationBlock() {
        ObjectNode cfg = json.createObjectNode();
        ObjectNode ui = json.createObjectNode();
        ObjectNode layout = json.createObjectNode();
        layout.put("type", "group");
        layout.put("title", "General");
        layout.put("width", "fill");
        ArrayNode gChildren = json.createArrayNode();
        ObjectNode panel = json.createObjectNode();
        panel.put("type", "panel");
        ArrayNode pChildren = json.createArrayNode();
        ObjectNode input = json.createObjectNode();
        input.put("type", "input");
        input.put("bind", "server.url");
        input.put("label", "URL");
        pChildren.add(input);
        panel.set("children", pChildren);
        gChildren.add(panel);
        layout.set("children", gChildren);
        ui.set("layout", layout);
        cfg.set("ui", ui);
        return cfg;
    }
}
