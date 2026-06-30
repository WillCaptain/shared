package org.twelve.aipp.widget;

import com.fasterxml.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AIPP Widget 协议规格验证器。
 *
 * <p>对应 {@link org.twelve.aipp.AippAppSpec} 在 App 层面的规格验证，
 * 本类专注于 Widget 维度的契约：manifest 结构（app_id / is_main / display_mode /
 * render / views / refresh_tool / upload）与 Theme CSS 变量（{@code --aipp-*}）。
 *
 * <p>历史说明：manifest 级 {@code supports: { disable, theme }} 声明块及配套的
 * disable 行为断言已移除——Host 端从未读取过该字段，主题变量由 Host 页面无条件注入。
 *
 * @see AippWidget
 * @see AippWidgetTheme
 */
public class AippWidgetSpec {

    // ══════════════════════════════════════════════════════════════════════════
    // 1. Theme CSS 变量映射规格
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 断言主题 JSON 对象包含所有必要的 CSS 变量键（{@code --aipp-*} 格式）。
     *
     * <p>适用于验证 host 在 DOM 注入时生成的 CSS 变量 Map 是否完整。
     * 合规的 CSS 变量 Map 应至少包含 {@link AippWidgetTheme#toCssVars()} 的全部 {@code --aipp-*} 键。
     *
     * @param cssVarsNode 主题 CSS 变量 JSON 对象（key 为 CSS property name）
     */
    public void assertThemeCssVarsComplete(JsonNode cssVarsNode) {
        String[] required = {
                "--aipp-bg", "--aipp-surface", "--aipp-surface2", "--aipp-surface3",
                "--aipp-text", "--aipp-text-dim", "--aipp-text-muted",
                "--aipp-border", "--aipp-border2",
                "--aipp-accent", "--aipp-accent-hover", "--aipp-accent-glow", "--aipp-active",
                "--aipp-danger", "--aipp-success", "--aipp-warning", "--aipp-info",
                "--aipp-font", "--aipp-font-mono",
                "--aipp-font-size", "--aipp-font-size-sm", "--aipp-font-size-lg",
                "--aipp-radius", "--aipp-radius-sm", "--aipp-radius-lg", "--aipp-radius-pill"
        };
        for (String key : required) {
            assertThat(cssVarsNode.has(key))
                    .as("[AIPP Widget Theme] CSS 变量 Map 缺少必要字段 '%s'。"
                            + "AippWidgetTheme.toCssVars() 应覆盖所有标准变量。", key)
                    .isTrue();
            assertThat(cssVarsNode.get(key).asText())
                    .as("[AIPP Widget Theme] CSS 变量 '%s' 值不能为空", key)
                    .isNotBlank();
        }
    }

    /**
     * 断言主题 record 中颜色字段均为合法 hex 格式（{@code #rrggbb} 或 {@code #rrggbbaa}）。
     *
     * @param theme 要验证的主题对象
     */
    public void assertThemeColorsAreValidHex(AippWidgetTheme theme) {
        assertValidHex("background",  theme.background());
        assertValidHex("surface",     theme.surface());
        assertValidHex("surface2",    theme.surface2());
        assertValidHex("surface3",    theme.surface3());
        assertValidHex("text",        theme.text());
        assertValidHex("textDim",     theme.textDim());
        assertValidHex("textMuted",   theme.textMuted());
        assertValidHex("border",      theme.border());
        assertValidHex("border2",     theme.border2());
        assertValidHex("accent",      theme.accent());
        assertValidHex("accentHover", theme.accentHover());
        assertValidHex("danger",      theme.danger());
        assertValidHex("success",     theme.success());
        assertValidHex("warning",     theme.warning());
        assertValidHex("info",        theme.info());
    }

    private void assertValidHex(String field, String value) {
        assertThat(value)
                .as("[AIPP Widget Theme] theme.%s='%s' 不是合法的 hex 颜色值（期望 #rrggbb 格式）",
                        field, value)
                .matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?");
    }

    /**
     * 断言语言代码为合法的 IETF 语言标签（如 {@code "zh"}、{@code "en"}、{@code "zh-CN"}）。
     */
    public void assertThemeLanguageValid(AippWidgetTheme theme) {
        assertThat(theme.language())
                .as("[AIPP Widget Theme] language='%s' 不是合法的语言代码（期望 IETF 标签如 zh, en, zh-CN）",
                        theme.language())
                .matches("[a-z]{2,3}(-[A-Z]{2})?");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. 辅助：widget manifest 中查找指定 type 的 widget
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 在 /api/widgets 响应中查找指定类型的 widget manifest。
     * 若未找到直接断言失败。
     */
    public JsonNode findWidget(JsonNode widgetsResponse, String widgetType) {
        for (JsonNode w : widgetsResponse.get("widgets")) {
            if (widgetType.equals(w.path("type").asText())) return w;
        }
        throw new AssertionError("[AIPP Widget] /api/widgets 中未找到 widget type: " + widgetType);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. View / Refresh 协议规格验证
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 断言 widget manifest 包含 {@code views} 数组，且每个视图具备必要字段。
     *
     * <p>每个视图条目必须包含：
     * <ul>
     *   <li>{@code id}       — 视图唯一标识（非空字符串）</li>
     *   <li>{@code label}    — 人类可读标签（非空字符串）</li>
     *   <li>{@code llm_hint} — LLM 上下文指令（非空字符串）</li>
     * </ul>
     *
     * @param widget widget manifest 节点
     */
    public void assertWidgetDeclaresViews(JsonNode widget) {
        String type = widget.path("type").asText("(unknown)");
        assertThat(widget.has("views"))
                .as("[AIPP Widget View] '%s'：缺少 'views' 字段。"
                        + "Widget 应声明支持的视图列表（多视图 widget）或空数组（单视图 widget）。", type)
                .isTrue();
        assertThat(widget.get("views").isArray())
                .as("[AIPP Widget View] '%s'：'views' 必须是数组", type)
                .isTrue();

        int idx = 0;
        for (JsonNode view : widget.get("views")) {
            final int i = idx++;
            assertThat(view.has("id") && !view.get("id").asText().isBlank())
                    .as("[AIPP Widget View] '%s'.views[%d] 缺少非空 'id' 字段", type, i).isTrue();
            assertThat(view.has("label") && !view.get("label").asText().isBlank())
                    .as("[AIPP Widget View] '%s'.views[%d] 缺少非空 'label' 字段", type, i).isTrue();
            assertThat(view.has("llm_hint") && !view.get("llm_hint").asText().isBlank())
                    .as("[AIPP Widget View] '%s'.views[%d] 缺少非空 'llm_hint' 字段", type, i).isTrue();
        }
    }

    /**
     * 断言 widget manifest 声明了 {@code refresh_tool}。
     *
     * <p><b>设计语义</b> — 与 {@code entry_tool} 分工不同：
     * <ul>
     *   <li>{@code entry_tool}：打开 / 进入 widget（Apps 面板、canvas 入口）</li>
     *   <li>{@code refresh_tool}：在 widget 已打开时重新拉取展示数据（编辑后刷新）</li>
     * </ul>
     *
     * <p>哪些工具会触发刷新由 <b>tool</b> 上的 {@code mutates_display: true} 声明（见
     * {@code spec/field-semantics.md}）。Host 在 LLM 调用了 {@code mutates_display} 工具但
     * 未主动调用 {@code refresh_tool} 时，可自动补调一次（兜底）。{@code views[].llm_hint}
     * 应使用 {@code {refresh_tool}} 占位符。
     *
     * <p>v2.8 起 legacy {@code refresh_skill} / {@code mutating_tools} 已整体移除，
     * 出现时由 {@link #assertWidgetUsesCompressedFields(JsonNode)} 拒绝。
     */
    public void assertWidgetDeclaresRefreshTool(JsonNode widget) {
        String type = widget.path("type").asText("(unknown)");
        assertThat(widget.has("refresh_tool") && !widget.get("refresh_tool").asText().isBlank())
                .as("[AIPP Widget View] '%s'：缺少 'refresh_tool' 字段。"
                        + "Widget 应声明用于刷新展示的工具名。", type)
                .isTrue();
    }

    /**
     * 断言 widget 包含指定的视图 id。
     *
     * @param widget    widget manifest
     * @param viewIds   期望存在的视图 id 列表
     */
    public void assertWidgetHasViews(JsonNode widget, String... viewIds) {
        assertWidgetDeclaresViews(widget);
        String type = widget.path("type").asText("(unknown)");
        java.util.Set<String> declared = new java.util.HashSet<>();
        for (JsonNode v : widget.get("views")) declared.add(v.path("id").asText());
        for (String viewId : viewIds) {
            assertThat(declared)
                    .as("[AIPP Widget View] '%s'：views 中未声明必要视图 id '%s'", type, viewId)
                    .contains(viewId);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    /** 合法的 widget {@code display_mode} 值。 */
    public static final java.util.Set<String> VALID_DISPLAY_MODES =
            java.util.Set.of("canvas", "chat", "pop");

    // 5. App Identity 规格（app_id / is_main / display_mode）
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 验证 widget manifest 包含 {@code app_id} 字段，且值非空。
     *
     * <p>每个 widget 必须声明归属的 AIPP 应用 ID，供 Host 的 Apps 面板做应用-widget 关联。
     */
    public void assertWidgetHasAppId(JsonNode widget) {
        String type = widget.path("type").asText("(unknown)");
        assertThat(widget.has("app_id"))
                .as("[AIPP App Identity] '%s'：缺少 'app_id' 字段。"
                        + "Widget 必须声明所属 AIPP 应用 ID（与 /api/app.app_id 一致）。", type)
                .isTrue();
        assertThat(widget.path("app_id").asText())
                .as("[AIPP App Identity] '%s'：'app_id' 不能为空", type)
                .isNotBlank();
    }

    /**
     * 验证 widget manifest 声明了 {@code is_main} 字段（boolean 类型）。
     *
     * <p>每个 app 最多只有一个 {@code is_main=true} 的 widget；
     * 没有主 widget 的 app 在 Apps 面板中点击图标时无法跳转。
     */
    public void assertWidgetDeclaresIsMain(JsonNode widget) {
        String type = widget.path("type").asText("(unknown)");
        assertThat(widget.has("is_main"))
                .as("[AIPP App Identity] '%s'：缺少 'is_main' 字段。"
                        + "Widget 必须显式声明是否为所属 app 的主界面（入口 widget）。", type)
                .isTrue();
        assertThat(widget.get("is_main").isBoolean())
                .as("[AIPP App Identity] '%s'：'is_main' 必须是 boolean 类型", type)
                .isTrue();
    }

    /**
     * 验证 widget manifest 声明了合法的 {@code display_mode}。
     *
     * <ul>
     *   <li>{@code canvas} — 全屏 canvas</li>
     *   <li>{@code chat}   — 聊天内嵌 html_widget 卡片</li>
     *   <li>{@code pop}    — 浮层，不进 chat/canvas 流</li>
     * </ul>
     */
    public void assertWidgetDeclaresDisplayMode(JsonNode widget) {
        String type = widget.path("type").asText("(unknown)");
        assertThat(widget.has("display_mode"))
                .as("[AIPP App Identity] '%s'：缺少 'display_mode' 字段。"
                        + "Widget 必须声明展示模式：canvas | chat | pop。", type)
                .isTrue();
        String mode = widget.path("display_mode").asText("").trim().toLowerCase();
        assertThat(VALID_DISPLAY_MODES)
                .as("[AIPP App Identity] '%s'：display_mode='%s' 不合法，必须是 %s 之一",
                        type, mode, VALID_DISPLAY_MODES)
                .contains(mode);
    }

    /**
     * 一次验证 widget 的全部 App Identity 字段（app_id + is_main + display_mode）。
     */
    public void assertWidgetHasFullAppIdentity(JsonNode widget) {
        assertWidgetHasAppId(widget);
        assertWidgetDeclaresIsMain(widget);
        assertWidgetDeclaresDisplayMode(widget);
    }

    /**
     * v2.6 压缩字段：拒绝 widget 根级的 legacy prompt / 入口字段。
     */
    public void assertWidgetUsesCompressedFields(JsonNode widget) {
        String type = widget.path("type").asText("(unknown)");
        assertThat(widget.has("renders_output_of_skill"))
                .as("[AIPP Widget] '%s'：'renders_output_of_skill' 已弃用，请改用 entry_tool", type)
                .isFalse();
        assertThat(widget.has("context_prompt"))
                .as("[AIPP Widget] '%s'：'context_prompt' 已弃用，请改用 widget_prompt", type)
                .isFalse();
        assertThat(widget.has("system_prompt"))
                .as("[AIPP Widget] '%s'：根级 'system_prompt' 已弃用，请改用 widget_prompt（view 级 system_prompt 仍允许）", type)
                .isFalse();
        assertThat(widget.has("refresh_skill"))
                .as("[AIPP Widget] '%s'：'refresh_skill' 已移除（v2.8），请改用 refresh_tool", type)
                .isFalse();
        assertThat(widget.has("mutating_tools"))
                .as("[AIPP Widget] '%s'：'mutating_tools' 已移除（v2.8），请在 /api/tools 的写工具上声明 mutates_display: true", type)
                .isFalse();
        assertThat(widget.has("is_canvas_mode"))
                .as("[AIPP Widget] '%s'：'is_canvas_mode' 已移除（v2.8），请改用 display_mode: canvas|chat|pop", type)
                .isFalse();
    }

    /** Canvas widget 应声明 {@code entry_tool}（打开时 Host 调用的 tool）。 */
    public void assertCanvasWidgetDeclaresEntryTool(JsonNode widget) {
        String type = widget.path("type").asText("(unknown)");
        String mode = widget.path("display_mode").asText("").trim().toLowerCase();
        if (!"canvas".equals(mode)) return;
        assertThat(widget.has("entry_tool"))
                .as("[AIPP Widget] '%s'：display_mode=canvas 的 widget 必须声明 entry_tool", type)
                .isTrue();
        assertThat(widget.path("entry_tool").asText("").trim())
                .as("[AIPP Widget] '%s'：entry_tool 不能为空", type)
                .isNotBlank();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 6. App-owned renderer 规格（render.kind/url）
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 验证 widget manifest 声明了由 AIPP app 自己提供的前端 renderer。
     *
     * <p>Host 只能根据此声明挂载 widget，不能在 host 代码中内置 app 专属 UI。
     * 合规结构：
     * <pre>
     * {
     *   "render": {
     *     "kind": "esm",
     *     "url":  "/widgets/action-list/action-list.js"
     *   }
     * }
     * </pre>
     *
     * <p>{@code sys.*} widget 是 host/system widget，可不声明 app-owned renderer。
     */
    public void assertWidgetDeclaresAppOwnedRenderer(JsonNode widget) {
        String type = widget.path("type").asText("(unknown)");
        assertThat(type.startsWith("sys."))
                .as("[AIPP Widget Renderer] '%s' 是 sys.* host widget，不应使用 app-owned renderer 断言。", type)
                .isFalse();
        assertThat(widget.has("render"))
                .as("[AIPP Widget Renderer] '%s'：缺少 'render'。"
                        + "AIPP app 自己相关的 UI 必须由 app 声明 renderer，Host 只负责挂载。", type)
                .isTrue();
        JsonNode render = widget.get("render");
        assertThat(render.isObject())
                .as("[AIPP Widget Renderer] '%s'：'render' 必须是对象", type)
                .isTrue();
        assertThat(render.has("kind"))
                .as("[AIPP Widget Renderer] '%s'：render 缺少 'kind'", type)
                .isTrue();
        String kind = render.path("kind").asText();
        assertThat(kind)
                .as("[AIPP Widget Renderer] '%s'：render.kind 必须是 esm", type)
                .isEqualTo("esm");
        assertThat(render.has("url"))
                .as("[AIPP Widget Renderer] '%s'：render 缺少 'url'", type)
                .isTrue();
        String url = render.path("url").asText();
        assertThat(url)
                .as("[AIPP Widget Renderer] '%s'：render.url 不能为空", type)
                .isNotBlank();
        assertThat(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/"))
                .as("[AIPP Widget Renderer] '%s'：render.url 必须是绝对 URL 或 app-relative path", type)
                .isTrue();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 7. html_widget 响应规格（display_mode=chat 的 widget 适用）
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 验证工具响应中包含合法的 {@code html_widget} 字段。
     *
     * <p>适用于 {@code display_mode: "chat"} 的 widget：工具响应以 app-owned widget 卡片形式嵌入聊天流。
     * 响应格式：
     * <pre>
     * {
     *   "html_widget": {
     *     "widget_type": "action-list",
     *     "title": "Action List",
     *     "data": { ... }
     *   }
     * }
     * </pre>
     *
     * @param toolName  工具名（用于错误信息）
     * @param response  工具响应 JSON
     */
    public void assertHtmlWidgetResponse(String toolName, JsonNode response) {
        assertThat(response.has("html_widget"))
                .as("[AIPP html_widget] '%s'：响应缺少 'html_widget' 字段。"
                        + "display_mode=chat 的 widget 必须通过 html_widget 返回 HTML 卡片内容。", toolName)
                .isTrue();

        JsonNode hw = response.get("html_widget");
        assertThat(hw.isObject())
                .as("[AIPP html_widget] '%s'：'html_widget' 必须是对象类型", toolName)
                .isTrue();

        assertThat(hw.has("widget_type") && hw.has("data"))
                .as("[AIPP html_widget] '%s'：'html_widget' 必须包含 Plan-D 字段 'widget_type' + 'data'",
                        toolName)
                .isTrue();

        assertThat(hw.path("widget_type").asText())
                .as("[AIPP html_widget] '%s'：'html_widget.widget_type' 不能为空", toolName)
                .isNotBlank();
        assertThat(hw.get("data").isObject())
                .as("[AIPP html_widget] '%s'：'html_widget.data' 必须是对象", toolName)
                .isTrue();

        if (hw.has("height")) {
            String h = hw.get("height").asText();
            assertThat(h)
                    .as("[AIPP html_widget] '%s'：'height' 不能为空字符串", toolName)
                    .isNotBlank();
        }
    }

    /**
     * 断言 chat-mode（display_mode=chat）工具响应不包含 canvas 字段。
     *
     * <p>Chat 内嵌模式的 widget 工具不应触发 canvas 事件，避免误切换到 Canvas Mode。
     */
    public void assertInlineWidgetResponseHasNoCanvas(String toolName, JsonNode response) {
        assertThat(response.has("canvas"))
                .as("[AIPP html_widget] '%s'：display_mode=chat 的工具响应不应包含 'canvas' 字段，"
                        + "否则会误触发 Canvas Mode 切换。", toolName)
                .isFalse();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 8. Upload 能力规格验证
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 断言 widget manifest 包含合法的顶层 {@code upload} 配置块。
     *
     * <p>合规的 upload 配置必须满足：
     * <ul>
     *   <li>存在 {@code upload} 字段且为对象</li>
     *   <li>{@code upload.accept} 为非空数组</li>
     *   <li>{@code upload.prompt} 为非空字符串</li>
     *   <li>{@code upload.tools} 为数组（可为空）</li>
     * </ul>
     */
    public void assertWidgetSupportsUpload(JsonNode widget) {
        String type = widget.path("type").asText("(unknown)");
        assertThat(widget.has("upload"))
                .as("[AIPP Widget Upload] '%s'：缺少顶层 'upload' 配置块。"
                        + "声明上传能力的 widget 必须在 manifest 中包含 upload 字段。", type)
                .isTrue();
        assertThat(widget.get("upload").isObject())
                .as("[AIPP Widget Upload] '%s'：'upload' 字段必须是对象", type)
                .isTrue();

        JsonNode upload = widget.get("upload");

        // accept
        assertThat(upload.has("accept") && upload.get("accept").isArray())
                .as("[AIPP Widget Upload] '%s'：upload.accept 必须是数组", type).isTrue();
        assertThat(upload.get("accept").size())
                .as("[AIPP Widget Upload] '%s'：upload.accept 不能为空", type).isGreaterThan(0);

        // prompt
        assertThat(upload.has("prompt") && !upload.get("prompt").asText().isBlank())
                .as("[AIPP Widget Upload] '%s'：upload.prompt 必须是非空字符串，"
                        + "用于告知 LLM 如何处理上传的文件内容。", type)
                .isTrue();

        // tools（允许为空数组，但必须是数组类型）
        if (upload.has("tools")) {
            assertThat(upload.get("tools").isArray())
                    .as("[AIPP Widget Upload] '%s'：upload.tools 必须是数组", type).isTrue();
        }
    }

    /**
     * 断言 upload.accept 中每个扩展名格式合法：必须以 {@code .} 开头、全小写、长度 2-10。
     *
     * <p>不支持二进制格式（如 .xlsx、.docx、.pdf），upload 仅用于文本文件。
     * 常见合法值：{@code .json}、{@code .csv}、{@code .txt}、{@code .yaml}。
     */
    public void assertUploadExtensionsWellFormed(JsonNode widget) {
        assertWidgetSupportsUpload(widget);
        String type = widget.path("type").asText("(unknown)");
        for (JsonNode ext : widget.get("upload").get("accept")) {
            String s = ext.asText();
            assertThat(s)
                    .as("[AIPP Widget Upload] '%s'：accept 中的扩展名 '%s' 格式不合法。"
                            + "必须以 '.' 开头、全小写字母、总长度 2–10，如 .json / .csv。", type, s)
                    .matches("\\.[a-z]{1,9}");
        }
    }

    /**
     * 断言 upload.accept 覆盖了所有指定的扩展名。
     *
     * @param widget     widget manifest
     * @param extensions 期望被声明的扩展名（如 {@code ".json", ".csv"}）
     */
    public void assertUploadAccepts(JsonNode widget, String... extensions) {
        assertWidgetSupportsUpload(widget);
        String type = widget.path("type").asText("(unknown)");
        java.util.Set<String> declared = new java.util.HashSet<>();
        for (JsonNode ext : widget.get("upload").get("accept")) declared.add(ext.asText());
        for (String required : extensions) {
            assertThat(declared)
                    .as("[AIPP Widget Upload] '%s'：upload.accept 未声明扩展名 '%s'", type, required)
                    .contains(required);
        }
    }

    /**
     * 断言 upload.tools 中包含指定的工具名。
     *
     * @param widget    widget manifest
     * @param toolNames 期望存在的工具名
     */
    public void assertUploadTools(JsonNode widget, String... toolNames) {
        assertWidgetSupportsUpload(widget);
        String type = widget.path("type").asText("(unknown)");
        JsonNode uploadNode = widget.get("upload");
        assertThat(uploadNode.has("tools") && uploadNode.get("tools").isArray())
                .as("[AIPP Widget Upload] '%s'：upload.tools 字段缺失或非数组", type).isTrue();
        java.util.Set<String> declared = new java.util.HashSet<>();
        for (JsonNode t : uploadNode.get("tools")) declared.add(t.asText());
        for (String required : toolNames) {
            assertThat(declared)
                    .as("[AIPP Widget Upload] '%s'：upload.tools 未声明工具 '%s'", type, required)
                    .contains(required);
        }
    }

    /**
     * 使用 {@link AippWidgetUpload} 对象验证消息组装结果的完整性。
     *
     * <p>组装后的消息必须同时包含文件名和文件内容。
     *
     * @param upload      upload 配置对象
     * @param fileName    文件名
     * @param fileContent 文件内容
     */
    public void assertUploadMessageAssembly(AippWidgetUpload upload,
                                            String fileName, String fileContent) {
        String msg = upload.assembleMessage(fileName, 0, fileContent);
        assertThat(msg)
                .as("[AIPP Widget Upload] assembleMessage 结果应包含文件名 '%s'", fileName)
                .contains(fileName);
        assertThat(msg)
                .as("[AIPP Widget Upload] assembleMessage 结果应包含文件内容")
                .contains(fileContent);
        assertThat(msg)
                .as("[AIPP Widget Upload] assembleMessage 结果应包含 upload.prompt 内容")
                .contains(upload.prompt());
    }
}
