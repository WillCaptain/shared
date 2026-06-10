package org.twelve.aipp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Host 运行时注入协议校验 — Host 在 install / env 变更时 {@code PUT /api/host/bindings}。
 *
 * <p>详见 {@code spec/host-injection.md} 与 {@code README.md} §6.10。
 *
 * <p>与 AIPP {@code configuration} 区分：{@code env} 等字段由 Host 注入，AIPP 不得持久化到 configuration values。
 */
public class AippHostInjectionSpec {

    private static final Set<String> REQUIRED_PUT_FIELDS = Set.of(
            "host_id",
            "app_id",
            "env"
    );

    /**
     * 校验 Host → AIPP {@code PUT /api/host/bindings} 请求体。
     */
    public void assertValidHostBindingsPutRequest(JsonNode body) {
        assertThat(body).as("[Host Injection] PUT body 不能为空").isNotNull();
        assertThat(body.isObject())
                .as("[Host Injection] PUT body 必须是 JSON 对象").isTrue();
        for (String key : REQUIRED_PUT_FIELDS) {
            assertThat(body.has(key))
                    .as("[Host Injection] PUT body 缺少 '%s'", key).isTrue();
            assertThat(body.path(key).asText("").trim())
                    .as("[Host Injection] '%s' 不能为空", key).isNotBlank();
        }
        assertValidEnv(body.path("env").asText());
    }

    /**
     * 校验 AIPP 对 {@code PUT /api/host/bindings} 的成功响应。
     */
    public void assertValidHostBindingsPutResponse(JsonNode response) {
        assertThat(response).as("[Host Injection] PUT 响应不能为空").isNotNull();
        assertThat(response.has("ok"))
                .as("[Host Injection] PUT 响应缺少 'ok'").isTrue();
        assertThat(response.get("ok").asBoolean())
                .as("[Host Injection] PUT 期望 ok=true").isTrue();
    }

    /**
     * 校验 {@code GET /api/host/bindings}（可选调试端点）响应形状。
     */
    public void assertValidHostBindingsGetResponse(JsonNode response) {
        assertThat(response).as("[Host Injection] GET 响应不能为空").isNotNull();
        assertThat(response.has("ok"))
                .as("[Host Injection] GET 响应缺少 'ok'").isTrue();
        assertThat(response.get("ok").asBoolean())
                .as("[Host Injection] GET 期望 ok=true").isTrue();
        if (response.has("bindings")) {
            assertThat(response.get("bindings").isObject())
                    .as("[Host Injection] 'bindings' 必须是对象").isTrue();
        }
    }

    public static void assertValidEnv(String env) {
        String normalized = env == null ? "" : env.trim().toLowerCase();
        assertThat(normalized)
                .as("[Host Injection] env 必须是 production / staging / draft")
                .isIn("production", "staging", "draft");
    }
}
