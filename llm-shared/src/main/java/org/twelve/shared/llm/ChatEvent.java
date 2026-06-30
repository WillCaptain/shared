package org.twelve.shared.llm;

/**
 * Agent 聊天 SSE 事件（Host ↔ 前端协议）。
 *
 * <p>与 {@link LLMCaller} 同属轻量 shared 层，供 world-one、entitir aip 等复用，
 * 避免 UI Host 为一条 record 依赖整包 ontology/aip。
 *
 * <h2>事件类型</h2>
 * <ul>
 *   <li>{@code tool_call}   — 即将调用某个工具（工具名），用于前端实时进度展示</li>
 *   <li>{@code thinking}    — LLM 推理过程（完整 reasoning_content，推理模型专用）</li>
 *   <li>{@code text_token}  — LLM 文本回复的单个流式 token（streaming 模式）</li>
 *   <li>{@code text}        — 完整文本（后端持久化，通常不下发 SSE）</li>
 *   <li>{@code html_widget} — Chat 内嵌 ESM 卡片（JSON 含 widget_type/data）</li>
 *   <li>{@code pop_widget}   — 浮窗模式（不占 chat/canvas；JSON 同 html_widget 形状）</li>
 *   <li>{@code canvas}      — canvas 指令 JSON，前端渲染 widget</li>
 *   <li>{@code session}     — 新 session 信号：{ui_session_id, name, type}</li>
 *   <li>{@code annotation}  — 灰色过程注解（路由、AIPP 匹配等）</li>
 *   <li>{@code error}       — 错误信息</li>
 *   <li>{@code client_tool_call} — 待本机 executor 执行的工具（JSON payload，见 aipp-protocol client-execution）</li>
 *   <li>{@code host_effect} — Host UI side effect（JSON，如 theme preset）；前端立即应用，不持久化到聊天历史</li>
 *   <li>{@code done}        — 流结束信号</li>
 * </ul>
 */
public record ChatEvent(Type type, String content) {

    public enum Type {
        TOOL_CALL, THINKING, TEXT_TOKEN, TEXT, CANVAS, HTML_WIDGET, POP_WIDGET, SESSION, ANNOTATION, ERROR,
        CLIENT_TOOL_CALL, CLIENT_INSTALL_OFFER, HOST_EFFECT, DONE
    }

    public static ChatEvent toolCall(String name)       { return new ChatEvent(Type.TOOL_CALL,  name); }
    public static ChatEvent thinking(String content)    { return new ChatEvent(Type.THINKING,   content); }
    public static ChatEvent textToken(String token)     { return new ChatEvent(Type.TEXT_TOKEN, token); }
    /** 完整文本信号（用于后端持久化，不下发前端）。 */
    public static ChatEvent text(String content)        { return new ChatEvent(Type.TEXT,       content); }
    public static ChatEvent canvas(String json)         { return new ChatEvent(Type.CANVAS,     json); }
    /** HTML 卡片内嵌聊天流（is_canvas_mode=false）；content 为 {"html":"...","height":"400px"} JSON。 */
    public static ChatEvent htmlWidget(String json)     { return new ChatEvent(Type.HTML_WIDGET, json); }
    /** 浮窗 widget（display_mode=pop）；content 为 {@code {"widget_type","title","data"}} JSON。 */
    public static ChatEvent popWidget(String json)      { return new ChatEvent(Type.POP_WIDGET, json); }
    public static ChatEvent session(String json)        { return new ChatEvent(Type.SESSION,    json); }
    /**
     * 灰色注解行（不属于最终回答），用于展示 AIPP 匹配、阶段跳转等过程信息。
     * content 格式：{"label":"world-entitir","detail":"EAI 员工入职链路"}
     */
    public static ChatEvent annotation(String content)  { return new ChatEvent(Type.ANNOTATION, content); }
    public static ChatEvent error(String message)       { return new ChatEvent(Type.ERROR,      message); }
    /** Client executor dispatch; {@code content} is JSON (session_id, call_id, tool, args, …). */
    public static ChatEvent clientToolCall(String json) { return new ChatEvent(Type.CLIENT_TOOL_CALL, json); }
    /** Offer to install a dual-surface tool's local package; {@code content} JSON (§8.6). */
    public static ChatEvent clientInstallOffer(String json) { return new ChatEvent(Type.CLIENT_INSTALL_OFFER, json); }
    /** Host UI side effect; {@code content} is JSON e.g. {@code {"type":"theme","preset":"light"}}. */
    public static ChatEvent hostEffect(String json)     { return new ChatEvent(Type.HOST_EFFECT, json); }
    public static ChatEvent done()                      { return new ChatEvent(Type.DONE,       ""); }

    /** SSE data 格式：{type, content} JSON。 */
    public String toSseData() {
        String escaped = content == null ? "" :
                content.replace("\\", "\\\\").replace("\"", "\\\"")
                       .replace("\n", "\\n").replace("\r", "");
        return "{\"type\":\"" + type.name().toLowerCase()
                + "\",\"content\":\"" + escaped + "\"}";
    }
}
