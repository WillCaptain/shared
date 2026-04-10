package org.twelve.aipp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通用 AIPP 协议规格验证器。
 *
 * <p>任何 AI Plugin Program（.aipp 应用）的测试类均可使用本工具类，
 * 通过调用其方法来验证自身 API 是否符合 AIPP 协议三层规范。
 *
 * <h2>用法</h2>
 * <pre>
 *   AippAppSpec spec = new AippAppSpec();
 *   spec.assertValidSkillsApiStructure(skillsJson);    // Layer 1+2 全量验证
 *   spec.assertValidWidgetsApiStructure(widgetsJson);
 *   spec.assertWidgetTypesRegistered(skillsJson, widgetsJson);
 * </pre>
 *
 * <h2>Skill 三层规格</h2>
 * <ul>
 *   <li><b>Layer 1（兼容层）</b>：name、description、parameters（OpenAI function-calling 格式）</li>
 *   <li><b>Layer 2（Mini-agent 层）</b>：prompt（执行指令）、tools（依赖 tool 列表）、
 *       resources（可选，数据源列表）</li>
 *   <li><b>Layer 3（AIPP 扩展层）</b>：canvas（widget 绑定）、session（会话管理）</li>
 * </ul>
 *
 * <h2>AIPP 核心接口</h2>
 * <ul>
 *   <li>GET /api/skills  — 声明 app 能力，每个 skill 含三层字段</li>
 *   <li>GET /api/widgets — 声明 widget 组件目录</li>
 *   <li>POST /api/tools/{name} — tool 调用（供 Widget ToolProxy 使用，不经 LLM）</li>
 * </ul>
 *
 * <h2>Canvas 触发规则</h2>
 * <ul>
 *   <li>canvas.triggers=true  → worldone 根据 Widget Manifest 生成 canvas 事件（skill 响应为纯数据）</li>
 *   <li>canvas.triggers=false → agent 保持 Chat Mode 或 fallback 生成 HTML</li>
 *   <li>canvas.widget_type 必须在 /api/widgets 中已注册</li>
 * </ul>
 */
public class AippAppSpec {

    /**
     * canvas.action 合法值集合。
     * <ul>
     *   <li>{@code open}    — 打开新 widget（推入导航栈）；sys.* widget 以模态覆盖层渲染</li>
     *   <li>{@code patch}   — 增量更新已有 widget 状态</li>
     *   <li>{@code replace} — 替换当前 widget（同类型时等同 patch）</li>
     *   <li>{@code close}   — 关闭当前 widget（弹出导航栈）</li>
     *   <li>{@code inline}  — 在 chat 消息流中嵌入轻量卡片（不进入导航栈）</li>
     * </ul>
     */
    private static final Set<String> VALID_CANVAS_ACTIONS = Set.of("open", "patch", "replace", "close", "inline");

    // ══════════════════════════════════════════════════════════════════════════
    // 1. GET /api/skills 结构规格
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 验证 /api/skills 响应的顶层结构。
     *
     * <p>必须包含：app（应用 ID）、version（版本）、skills（技能列表）。
     * 每个 skill 必须包含：name、description、parameters、canvas。
     */
    public void assertValidSkillsApiStructure(JsonNode skillsResponse) {
        assertThat(skillsResponse.has("app"))
                .as("[AIPP] /api/skills 响应缺少 'app' 字段").isTrue();
        assertThat(skillsResponse.has("version"))
                .as("[AIPP] /api/skills 响应缺少 'version' 字段").isTrue();
        assertThat(skillsResponse.has("skills"))
                .as("[AIPP] /api/skills 响应缺少 'skills' 字段").isTrue();
        assertThat(skillsResponse.get("skills").isArray())
                .as("[AIPP] 'skills' 字段必须是数组").isTrue();
        assertThat(skillsResponse.get("skills").size())
                .as("[AIPP] 'skills' 数组不能为空").isGreaterThan(0);

        for (JsonNode skill : skillsResponse.get("skills")) {
            assertValidSkillStructure(skill);
        }
    }

    /**
     * 验证单个 skill 对象的完整三层结构。
     *
     * <ul>
     *   <li>Layer 1：name、description、parameters（OpenAI function schema）</li>
     *   <li>Layer 2：prompt（执行指令）、tools（依赖 tool 列表）</li>
     *   <li>Layer 3：canvas（AIPP widget 绑定声明）</li>
     * </ul>
     */
    public void assertValidSkillStructure(JsonNode skill) {
        String skillName = skill.has("name") ? skill.get("name").asText() : "(unknown)";

        // Layer 1
        assertThat(skill.has("name"))
                .as("[AIPP] skill 缺少 'name' 字段").isTrue();
        assertThat(skill.has("description"))
                .as("[AIPP] skill '%s' 缺少 'description' 字段", skillName).isTrue();
        assertThat(skill.get("description").asText())
                .as("[AIPP] skill '%s' 的 description 不能为空", skillName).isNotBlank();
        assertThat(skill.has("parameters"))
                .as("[AIPP] skill '%s' 缺少 'parameters' 字段", skillName).isTrue();
        assertThat(skill.has("canvas"))
                .as("[AIPP] skill '%s' 缺少 'canvas' 字段（AIPP 规格要求所有 skill 声明 canvas 元数据）", skillName)
                .isTrue();

        assertValidParametersSchema(skillName, skill.get("parameters"));
        assertValidSkillCanvasDeclaration(skillName, skill.get("canvas"));

        // Layer 2
        assertValidSkillLayer2(skill);
    }

    /**
     * Layer 2 规格：验证 skill 的 mini-agent 执行层字段。
     *
     * <p>规则：
     * <ul>
     *   <li>prompt 必须存在且不为空（描述该 skill 的执行逻辑）</li>
     *   <li>tools 必须存在且为数组（声明依赖的原子 tool 列表，可以为空数组）</li>
     *   <li>resources 如果存在，必须为数组（声明可读数据源，可选字段）</li>
     * </ul>
     */
    public void assertValidSkillLayer2(JsonNode skill) {
        String skillName = skill.path("name").asText("(unknown)");

        assertThat(skill.has("prompt"))
                .as("[AIPP Layer 2] skill '%s' 缺少 'prompt' 字段（mini-agent 执行指令）", skillName)
                .isTrue();
        assertThat(skill.get("prompt").asText())
                .as("[AIPP Layer 2] skill '%s' 的 prompt 不能为空", skillName)
                .isNotBlank();

        assertThat(skill.has("tools"))
                .as("[AIPP Layer 2] skill '%s' 缺少 'tools' 字段（依赖 tool 列表，可为空数组）", skillName)
                .isTrue();
        assertThat(skill.get("tools").isArray())
                .as("[AIPP Layer 2] skill '%s' tools 必须是数组", skillName)
                .isTrue();

        if (skill.has("resources")) {
            assertThat(skill.get("resources").isArray())
                    .as("[AIPP Layer 2] skill '%s' resources 必须是数组", skillName)
                    .isTrue();
        }
    }

    /**
     * Layer 3 规格：验证 AIPP skill 的 session 扩展声明（可选字段）。
     *
     * <p>若 skill 声明了 session 扩展，则必须至少包含 creates_on 或 loads_on 之一，
     * 说明该 skill 在何种条件下创建或加载 session。
     */
    public void assertValidSkillSessionExtension(JsonNode skill) {
        String skillName = skill.path("name").asText("(unknown)");
        if (!skill.has("session")) return;

        JsonNode session = skill.get("session");
        boolean hasCondition = session.has("creates_on") || session.has("loads_on");
        assertThat(hasCondition)
                .as("[AIPP Layer 3] skill '%s' 声明了 session 扩展，"
                        + "但未包含 creates_on 或 loads_on 任何一个会话条件", skillName)
                .isTrue();
    }

    /**
     * 验证 skill parameters 是合法的 OpenAI function schema 格式。
     */
    public void assertValidParametersSchema(String skillName, JsonNode parameters) {
        assertThat(parameters.has("type"))
                .as("[AIPP] skill '%s' parameters 缺少 'type' 字段", skillName).isTrue();
        assertThat(parameters.get("type").asText())
                .as("[AIPP] skill '%s' parameters.type 必须为 'object'", skillName)
                .isEqualTo("object");
        assertThat(parameters.has("properties"))
                .as("[AIPP] skill '%s' parameters 缺少 'properties' 字段", skillName).isTrue();
        assertThat(parameters.has("required"))
                .as("[AIPP] skill '%s' parameters 缺少 'required' 字段", skillName).isTrue();
        assertThat(parameters.get("required").isArray())
                .as("[AIPP] skill '%s' parameters.required 必须是数组", skillName).isTrue();
    }

    /**
     * 验证 skill canvas 声明结构。
     *
     * <p>规则：
     * <ul>
     *   <li>canvas.triggers 必须存在且为 boolean</li>
     *   <li>triggers=true 时：必须有 widget_type；如果有 action，必须合法</li>
     *   <li>triggers=false 时：不应有 widget_type 和 action</li>
     * </ul>
     */
    public void assertValidSkillCanvasDeclaration(String skillName, JsonNode canvas) {
        assertThat(canvas.has("triggers"))
                .as("[AIPP] skill '%s' canvas 缺少 'triggers' 字段", skillName).isTrue();
        assertThat(canvas.get("triggers").isBoolean())
                .as("[AIPP] skill '%s' canvas.triggers 必须是 boolean 类型", skillName).isTrue();

        boolean triggers = canvas.get("triggers").asBoolean();
        if (triggers) {
            assertThat(canvas.has("widget_type"))
                    .as("[AIPP] skill '%s' canvas.triggers=true 时必须声明 widget_type", skillName)
                    .isTrue();
            assertThat(canvas.get("widget_type").asText())
                    .as("[AIPP] skill '%s' canvas.widget_type 不能为空", skillName)
                    .isNotBlank();
            if (canvas.has("action")) {
                assertThat(VALID_CANVAS_ACTIONS)
                        .as("[AIPP] skill '%s' canvas.action 必须为 %s 之一", skillName, VALID_CANVAS_ACTIONS)
                        .contains(canvas.get("action").asText());
            }
        } else {
            assertThat(canvas.has("widget_type"))
                    .as("[AIPP] skill '%s' canvas.triggers=false 时不应有 widget_type（agent 走 Chat/HTML fallback 路径）",
                            skillName)
                    .isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. GET /api/widgets 结构规格
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 验证 /api/widgets 响应的结构。
     *
     * <p>必须包含：app、widgets（数组）。
     * 每个 widget 必须包含：id（全局唯一）、source（资源路径）。
     */
    public void assertValidWidgetsApiStructure(JsonNode widgetsResponse) {
        assertThat(widgetsResponse.has("app"))
                .as("[AIPP] /api/widgets 响应缺少 'app' 字段").isTrue();
        assertThat(widgetsResponse.has("widgets"))
                .as("[AIPP] /api/widgets 响应缺少 'widgets' 字段").isTrue();
        assertThat(widgetsResponse.get("widgets").isArray())
                .as("[AIPP] 'widgets' 字段必须是数组").isTrue();
        assertThat(widgetsResponse.get("widgets").size())
                .as("[AIPP] 'widgets' 数组不能为空").isGreaterThan(0);

        for (JsonNode widget : widgetsResponse.get("widgets")) {
            assertValidWidgetStructure(widget);
        }
    }

    /**
     * 验证单个 widget 对象的结构。
     *
     * <p>Widget 用 {@code type} 作为全局唯一标识符（如 "entity-graph"）。
     * {@code source} 声明渲染方式（builtin / url / iframe）。
     */
    public void assertValidWidgetStructure(JsonNode widget) {
        String widgetType = widget.has("type") ? widget.get("type").asText() : "(unknown)";
        assertThat(widget.has("type"))
                .as("[AIPP] widget 缺少 'type' 字段（全局唯一标识符）").isTrue();
        assertThat(widget.get("type").asText())
                .as("[AIPP] widget type 不能为空").isNotBlank();
        assertThat(widget.has("source"))
                .as("[AIPP] widget '%s' 缺少 'source' 字段（builtin/url/iframe）", widgetType).isTrue();
        assertThat(widget.get("source").asText())
                .as("[AIPP] widget '%s' source 不能为空", widgetType).isNotBlank();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. 跨接口一致性校验
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 验证 skills 中引用的所有 widget_type 均已在 /api/widgets 中注册。
     *
     * <p>AIPP 规格要求：canvas.widget_type 必须与 Widget Manifest 中的 type 对应，
     * 否则 agent 无法找到 renderer，会 fallback 到生成 HTML。
     *
     * <p><b>豁免规则</b>：{@code sys.*} 前缀的 widget 类型为 world-one 系统内置，
     * 无需也不能在 /api/widgets 中注册，检查时自动跳过。
     *
     * @see AippSystemWidget
     */
    public void assertWidgetTypesRegistered(JsonNode skillsResponse, JsonNode widgetsResponse) {
        Set<String> registeredIds = new HashSet<>();
        for (JsonNode w : widgetsResponse.get("widgets")) {
            if (w.has("type")) registeredIds.add(w.get("type").asText());
        }

        for (JsonNode skill : skillsResponse.get("skills")) {
            JsonNode canvas = skill.get("canvas");
            if (canvas.get("triggers").asBoolean() && canvas.has("widget_type")) {
                String widgetType = canvas.get("widget_type").asText();
                String skillName  = skill.get("name").asText();

                // sys.* 为系统内置 widget，豁免注册检查
                if (AippSystemWidget.isSystemWidget(widgetType)) continue;

                assertThat(registeredIds)
                        .as("[AIPP] skill '%s' 引用了 widget_type='%s'，但该类型未在 /api/widgets 中注册。"
                                + "Agent 将 fallback 到生成 HTML，可能影响用户体验。",
                                skillName, widgetType)
                        .contains(widgetType);
            }
        }
    }

    /**
     * 断言给定的 widget_type 不使用系统保留前缀 {@code sys.*}。
     *
     * <p>AIPP 应用注册自己的 widget 时不得使用 {@code sys.*} 前缀，
     * 该前缀为 world-one 系统内置 widget 保留（{@link AippSystemWidget}）。
     *
     * @param widgetType 要检查的 widget 类型字符串
     */
    public void assertSystemWidgetExempt(String widgetType) {
        assertThat(AippSystemWidget.isSystemWidget(widgetType))
                .as("[AIPP] widget_type='%s' 使用了系统保留前缀 'sys.'，"
                        + "AIPP 应用不得注册 sys.* 类型的 widget。"
                        + "系统内置 widget 类型请参考 AippSystemWidget 常量。", widgetType)
                .isFalse();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. 工具响应 vs skill 声明一致性
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 验证工具的实际响应与 skill 的 canvas 声明一致。
     *
     * <p>核心规则：
     * <ul>
     *   <li>triggers=true  → 响应必须有 canvas 字段，且 action/widget_type 与声明匹配</li>
     *   <li>triggers=false → 响应不得有 canvas 字段（Chat Mode 或 HTML fallback）</li>
     * </ul>
     *
     * @param toolName     工具名，用于错误信息
     * @param skillCanvas  来自 /api/skills 的 canvas 声明对象
     * @param toolResponse 工具执行返回的 JSON 响应
     */
    public void assertToolResponseMatchesSkillCanvas(
            String toolName, JsonNode skillCanvas, JsonNode toolResponse) {

        boolean triggersDeclared = skillCanvas.get("triggers").asBoolean();

        if (triggersDeclared) {
            assertThat(toolResponse.has("canvas"))
                    .as("[AIPP] '%s'：skill 声明 triggers=true，但工具响应中没有 canvas 字段。"
                            + "Agent 将停留在 Chat Mode，Widget 不会渲染。", toolName)
                    .isTrue();

            assertValidToolResponseCanvas(toolName, toolResponse.get("canvas"));

            if (skillCanvas.has("action")) {
                String expectedAction = skillCanvas.get("action").asText();
                String actualAction   = toolResponse.get("canvas").get("action").asText();
                assertThat(actualAction)
                        .as("[AIPP] '%s'：canvas.action 与 skill 声明不匹配（期望=%s，实际=%s）",
                                toolName, expectedAction, actualAction)
                        .isEqualTo(expectedAction);
            }

            if (skillCanvas.has("widget_type")) {
                String expectedType = skillCanvas.get("widget_type").asText();
                String actualType   = toolResponse.get("canvas").get("widget_type").asText();
                assertThat(actualType)
                        .as("[AIPP] '%s'：canvas.widget_type 与 skill 声明不匹配（期望=%s，实际=%s）",
                                toolName, expectedType, actualType)
                        .isEqualTo(expectedType);
            }

        } else {
            assertThat(toolResponse.has("canvas"))
                    .as("[AIPP] '%s'：skill 声明 triggers=false（Chat Mode），"
                            + "但工具响应中存在 canvas 字段。"
                            + "这会导致 agent 误判进入 Canvas Mode，违反 AIPP P2 原则。", toolName)
                    .isFalse();
        }
    }

    /**
     * 验证工具响应中 canvas 对象的内部结构。
     */
    public void assertValidToolResponseCanvas(String toolName, JsonNode canvas) {
        assertThat(canvas.has("action"))
                .as("[AIPP] '%s'：canvas 缺少 'action' 字段", toolName).isTrue();
        assertThat(VALID_CANVAS_ACTIONS)
                .as("[AIPP] '%s'：canvas.action='%s' 不合法，必须为 %s 之一",
                        toolName, canvas.path("action").asText(), VALID_CANVAS_ACTIONS)
                .contains(canvas.get("action").asText());
        assertThat(canvas.has("widget_type"))
                .as("[AIPP] '%s'：canvas 缺少 'widget_type' 字段", toolName).isTrue();
        assertThat(canvas.get("widget_type").asText())
                .as("[AIPP] '%s'：canvas.widget_type 不能为空", toolName).isNotBlank();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. 具名场景断言（语义级）
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 断言响应处于 Chat Mode（无 canvas，无 new_session）。
     *
     * <p>适用于纯查询类 API（如 world_list）：agent 保持对话，不触发 Widget 渲染。
     * 若需展示数据，由 LLM 使用自然语言描述，或 fallback 到生成 HTML。
     *
     * @param toolName 工具名（用于错误提示）
     * @param response 工具执行返回的 JSON 响应
     */
    public void assertChatModeResponse(String toolName, JsonNode response) {
        assertThat(response.has("canvas"))
                .as("[AIPP] '%s'：Chat Mode 响应不应包含 canvas 字段（canvas ≠ open）", toolName)
                .isFalse();
        assertThat(response.has("new_session"))
                .as("[AIPP] '%s'：Chat Mode 响应不应包含 new_session 字段（session ≠ new）", toolName)
                .isFalse();
    }

    /**
     * 断言响应以 canvas.action=open 进入 Canvas Mode，并创建了新 session。
     *
     * <p>适用于创建类 API（如 world_create_session）：响应必须包含：
     * <ul>
     *   <li>canvas.action = "open"（打开新 Widget 实例）</li>
     *   <li>canvas.widget_type = expectedWidgetType（对应注册的 Widget）</li>
     *   <li>canvas.widget_id（非空，由服务端生成）</li>
     *   <li>new_session（表示创建了新会话，session = new）</li>
     * </ul>
     *
     * @param toolName           工具名
     * @param response           工具响应 JSON
     * @param expectedWidgetType 期望的 widget 类型（如 "entity-graph"）
     */
    public void assertCanvasOpenWithNewSession(
            String toolName, JsonNode response, String expectedWidgetType) {

        assertThat(response.has("canvas"))
                .as("[AIPP] '%s'：应进入 Canvas Mode，但响应中没有 canvas 字段", toolName)
                .isTrue();
        assertThat(response.has("new_session"))
                .as("[AIPP] '%s'：应创建新 session，但响应中没有 new_session 字段（session = new 规格失败）",
                        toolName)
                .isTrue();

        JsonNode canvas = response.get("canvas");
        assertThat(canvas.get("action").asText())
                .as("[AIPP] '%s'：canvas.action 应为 'open'（canvas == open 规格）", toolName)
                .isEqualTo("open");
        assertThat(canvas.get("widget_type").asText())
                .as("[AIPP] '%s'：canvas.widget_type 应为 '%s'（widget == %s 规格）",
                        toolName, expectedWidgetType, expectedWidgetType)
                .isEqualTo(expectedWidgetType);
        assertThat(canvas.has("widget_id"))
                .as("[AIPP] '%s'：canvas 应包含 widget_id", toolName).isTrue();
        assertThat(canvas.get("widget_id").asText())
                .as("[AIPP] '%s'：canvas.widget_id 不能为空", toolName).isNotBlank();
    }

    /**
     * 断言响应以 canvas.action=patch 增量更新已有 Widget。
     *
     * <p>适用于修改类 API（如 world_add_definition）。
     *
     * @param toolName           工具名
     * @param response           工具响应 JSON
     * @param expectedWidgetType 期望的 widget 类型
     */
    public void assertCanvasPatchResponse(
            String toolName, JsonNode response, String expectedWidgetType) {

        assertThat(response.has("canvas"))
                .as("[AIPP] '%s'：应返回 canvas.patch 指令，但响应中没有 canvas 字段", toolName)
                .isTrue();

        JsonNode canvas = response.get("canvas");
        assertThat(canvas.get("action").asText())
                .as("[AIPP] '%s'：canvas.action 应为 'patch'", toolName)
                .isEqualTo("patch");
        assertThat(canvas.get("widget_type").asText())
                .as("[AIPP] '%s'：canvas.widget_type 应为 '%s'", toolName, expectedWidgetType)
                .isEqualTo(expectedWidgetType);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 6. 辅助工具方法
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 验证 {@code GET /api/app} 响应的结构（AIPP App Manifest 规格）。
     *
     * <p>必须包含：app_id、app_name、app_icon、app_description、app_color、is_active、version。
     *
     * <pre>
     * {
     *   "app_id":          "memory-one",
     *   "app_name":        "记忆管理",
     *   "app_icon":        "&lt;svg ...&gt;...&lt;/svg&gt;",
     *   "app_description": "管理 AI Agent 的长期记忆",
     *   "app_color":       "#7c6ff7",
     *   "is_active":       true,
     *   "version":         "1.0"
     * }
     * </pre>
     */
    public void assertValidAppManifest(JsonNode appManifest) {
        for (String required : new String[]{"app_id", "app_name", "app_icon", "app_description", "app_color", "is_active", "version"}) {
            assertThat(appManifest.has(required))
                    .as("[AIPP App] /api/app 响应缺少 '%s' 字段", required)
                    .isTrue();
        }
        assertThat(appManifest.path("app_id").asText())
                .as("[AIPP App] 'app_id' 不能为空").isNotBlank();
        assertThat(appManifest.path("app_name").asText())
                .as("[AIPP App] 'app_name' 不能为空").isNotBlank();
        assertThat(appManifest.path("app_icon").asText())
                .as("[AIPP App] 'app_icon' 不能为空").isNotBlank();
        assertThat(appManifest.path("app_description").asText())
                .as("[AIPP App] 'app_description' 不能为空").isNotBlank();
        assertThat(appManifest.path("app_color").asText())
                .as("[AIPP App] 'app_color' 不能为空（期望 hex 格式，如 #7c6ff7）").isNotBlank();
        assertThat(appManifest.get("is_active").isBoolean())
                .as("[AIPP App] 'is_active' 必须是 boolean 类型").isTrue();
        assertThat(appManifest.path("version").asText())
                .as("[AIPP App] 'version' 不能为空").isNotBlank();

        // app_id 必须与 /api/skills 的 app 字段一致（跨接口一致性）
        String appId = appManifest.path("app_id").asText();
        assertThat(appId)
                .as("[AIPP App] 'app_id' 格式应为 kebab-case（小写字母+连字符），"
                        + "如 'memory-one'、'world-entitir'")
                .matches("[a-z][a-z0-9\\-]*");
    }

    /**
     * 验证 /api/app 中的 app_id 与 /api/skills 中的 app 字段一致。
     *
     * <p>跨接口一致性约束：同一个 AIPP 应用在所有端点中应使用相同的标识符。
     */
    public void assertAppIdConsistency(JsonNode appManifest, JsonNode skillsResponse) {
        String manifestAppId = appManifest.path("app_id").asText();
        String skillsApp     = skillsResponse.path("app").asText();
        assertThat(manifestAppId)
                .as("[AIPP App] /api/app.app_id（'%s'）与 /api/skills.app（'%s'）不一致，"
                        + "同一 AIPP 应用在所有端点中应使用相同标识符。", manifestAppId, skillsApp)
                .isEqualTo(skillsApp);
    }

    /**
     * 验证 /api/widgets 中每个 widget 都声明了 App Identity 字段（app_id / is_main / is_canvas_mode）。
     *
     * <p>补充 {@link #assertValidWidgetsApiStructure(JsonNode)} 的验证，
     * 新增对 App Identity 三字段的存在性和类型校验。
     */
    public void assertWidgetsHaveAppIdentityFields(JsonNode widgetsResponse) {
        assertThat(widgetsResponse.has("widgets")).isTrue();
        for (JsonNode widget : widgetsResponse.get("widgets")) {
            String type = widget.path("type").asText("(unknown)");

            assertThat(widget.has("app_id"))
                    .as("[AIPP App Identity] widget '%s' 缺少 'app_id' 字段", type).isTrue();
            assertThat(widget.path("app_id").asText())
                    .as("[AIPP App Identity] widget '%s' 'app_id' 不能为空", type).isNotBlank();

            assertThat(widget.has("is_main"))
                    .as("[AIPP App Identity] widget '%s' 缺少 'is_main' 字段", type).isTrue();
            assertThat(widget.get("is_main").isBoolean())
                    .as("[AIPP App Identity] widget '%s' 'is_main' 必须是 boolean", type).isTrue();

            assertThat(widget.has("is_canvas_mode"))
                    .as("[AIPP App Identity] widget '%s' 缺少 'is_canvas_mode' 字段", type).isTrue();
            assertThat(widget.get("is_canvas_mode").isBoolean())
                    .as("[AIPP App Identity] widget '%s' 'is_canvas_mode' 必须是 boolean", type).isTrue();
        }
    }

    /**
     * 验证每个 app 在 /api/widgets 中恰好有一个 is_main=true 的 widget。
     *
     * <p>AIPP 协议强制要求：每个注册了 {@code GET /api/app} 的 AIPP 服务，
     * 必须在其 {@code /api/widgets} 中声明恰好一个 {@code is_main=true} 的 widget，
     * 作为用户从 Apps 面板点击进入的主入口。
     *
     * <p>没有 UI 的纯工具服务不应注册 app manifest，只提供 /api/skills 即可。
     *
     * @param widgetsResponse {@code /api/widgets} 的完整响应
     * @param appIds          本次需要验证的 app_id 集合（通常来自 /api/app）
     */
    public void assertExactlyOneMainWidget(JsonNode widgetsResponse,
                                           java.util.Collection<String> appIds) {
        java.util.Map<String, Integer> mainCount = new java.util.HashMap<>();
        for (String id : appIds) mainCount.put(id, 0);
        for (JsonNode widget : widgetsResponse.get("widgets")) {
            String appId = widget.path("app_id").asText();
            if (mainCount.containsKey(appId) && widget.path("is_main").asBoolean(false)) {
                mainCount.merge(appId, 1, Integer::sum);
            }
        }
        for (java.util.Map.Entry<String, Integer> e : mainCount.entrySet()) {
            assertThat(e.getValue())
                    .as("[AIPP] app '%s' 有 %d 个 is_main=true 的 widget，"
                            + "每个 AIPP 必须恰好声明一个主 widget（is_main=true）。",
                            e.getKey(), e.getValue())
                    .isEqualTo(1);
        }
    }

    /**
     * @deprecated 弱约束，请改用 {@link #assertExactlyOneMainWidget}。
     */
    @Deprecated
    public void assertAtMostOneMainWidget(JsonNode widgetsResponse) {
        java.util.Map<String, Integer> mainCount = new java.util.HashMap<>();
        for (JsonNode widget : widgetsResponse.get("widgets")) {
            String appId = widget.path("app_id").asText();
            if (widget.path("is_main").asBoolean(false)) {
                mainCount.merge(appId, 1, Integer::sum);
            }
        }
        for (java.util.Map.Entry<String, Integer> e : mainCount.entrySet()) {
            assertThat(e.getValue())
                    .as("[AIPP App Identity] app '%s' 有 %d 个 is_main=true 的 widget，"
                            + "每个 app 最多只能有一个主 widget。", e.getKey(), e.getValue())
                    .isLessThanOrEqualTo(1);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 7. 辅助工具方法
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 在 /api/skills 响应中查找指定名称的 skill。
     * 若找不到，直接断言失败。
     */
    public JsonNode findSkill(JsonNode skillsResponse, String skillName) {
        for (JsonNode skill : skillsResponse.get("skills")) {
            if (skillName.equals(skill.path("name").asText())) {
                return skill;
            }
        }
        throw new AssertionError("[AIPP] /api/skills 中未找到 skill: " + skillName);
    }
}
