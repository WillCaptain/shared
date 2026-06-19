# Host ↔ AIPP 运行时契约（chat / open / ChatEvent / prompt 层）

> **Discovery:** [`AGENTS.md`](../AGENTS.md) → [`INDEX.md`](INDEX.md) → this file.
> 本文是协议的"运行时入口"——AIPP 端写完 manifest 定义后，要让用户在 host 里真的能聊起来 / 看到 widget / 点交互，必须遵守本文契约。Host 必须实现，AIPP 必须按这套消费/产出。
> Widget ESM / `hostApi` 协议：[`widgets.md`](widgets.md) §2–§3。Tool 响应字段：[`tool-responses.md`](tool-responses.md)。

---

## 1. 主对话端点 `POST /api/chat`（Host 实现，AIPP 知晓）

请求：

```json
{
  "session_id":  "main | task-... | app-recipe-one | <UUID>",
  "message":     "用户原文",
  "widget_view": { "widget_type": "recipe-board", "view_id": "FAVORITE" }
}
```

响应：`text/event-stream`，每条一行 `data: <ChatEvent JSON>\n\n`。

### ChatEvent 类型清单（AIPP 必须知道的，按到达顺序可能交错出现）

| `type` | 何时发出 | content 形态 | AIPP 触发方式 |
|---|---|---|---|
| `text_token` | LLM 流式吐 token | 字符串片段 | LLM 自由生成 |
| `thinking` | LLM 推理流 | 字符串片段 | LLM 自由生成 |
| `tool_call` | 即将调一个 tool（含 universal） | `{ tool, args }` JSON | LLM 决定调用 |
| `annotation` | Router/Skill/系统注解（如"Router: load skill X"） | 字符串 | host 元数据 |
| `html_widget` | tool 响应根含 `html_widget` | `{ widget_type, title, data }` JSON | **AIPP 在 tool 响应根写 `html_widget`** |
| `canvas` | tool 响应触发 canvas 模式 | `{ action, widget_type, session_id, data }` JSON | **AIPP 写 `canvas` 或满足 `output_widget_rules.force_canvas_when`** |
| `session` | 新建/切换 task/app session | `{ name, type, app_id, canvas_session_id, widget_type, welcome_message, session_policy, session_instance_key }` | **AIPP 在 tool 响应写 `new_session` 或返回带 widget_type 的非新建** |
| `done` | 本轮结束 | `{}` | host 自动 |
| `error` | 异常 | `{ message }` | host 自动 |

→ AIPP 端要做的就是**写对 tool 响应**（[`tool-responses.md`](tool-responses.md)）：写 `html_widget` 就出现卡片，写 `canvas` 或满足 force_canvas 就出现全屏 widget，写 `new_session` 就切换会话。**永远不要直接生成 ChatEvent**——那是 host 的事。

---

## 2. 直调 tool 端点 `POST /api/apps/{appId}/open`（Host 实现）

绕过 LLM 直接调一个 tool，常用于：
- 用户点 Apps 面板里的应用图标 → host 调 AIPP 主入口 widget tool
- html_widget 卡片里的按钮通过 `hostApi.callTool(...)` 触发（[`widgets.md`](widgets.md) §3）

请求：

```json
{ "tool_name": "recipe_list_view", "tool_args": { /* ... */ }, "session_id": "<可选>" }
```

响应同 `/api/chat`：SSE 流，事件类型与 §1 相同。

→ AIPP 端**无需特殊适配**：host 内部其实就是构造一次 tool call → 调 AIPP 的 `POST /api/tools/{name}` → 把 tool 响应转成 SSE 事件。AIPP 只要让 tool 实现稳定即可。

---

## 3. Widget 挂载与数据传递

- `html_widget` / canvas widget 都通过同一套 ES module mount 协议挂载；Host 根据 `widget_type` 找到 `/api/widgets` 中的 manifest，动态 import `render.url`，调用 `mount(targetEl, hostApi, data)` — 完整契约见 [`widgets.md`](widgets.md) §2–§3。
- Host 把 canvas payload 原样合并进 `data`，不得理解 app 专属字段；已挂载 widget 上可调用通用的 widget-owned 入口（如 `aippHandleCanvasCommand(cmd)`）。
- entry prompt 动态激活（`entry_prompt_hit` 的运行时语义）：[`tool-responses.md`](tool-responses.md) §5.1。
- Widget upload 的 mini-agent 编排（`upload.prompt` / `upload.tools`）：[`widgets.md`](widgets.md) §6。

---

## 4. LLM Context 多层架构（Host 视角）

Host 每轮对话的 prompt 按 6 层叠加（顺序固定）：

```
Layer 0：Host base + ambient/entry prompt + Widget manual / view prompt
         （Host 铁律 + 各 AIPP 的 prompt_contributions[layer=ambient_prompt])

Layer 1：Memory Context（用户长期画像，可选）

Layer 2：Session Entry Prompt（task/event/app session 专有）

Layer 3：Widget llm_hint + Workspace info（仅 canvas 激活时）

Layer 4：Skill Playbook（progressive disclosure 加载的 SKILL.md）

Layer 5：UI Hints（最高优先级，前置到 sysContent 最前）

────────────────────────────────────────────
Layer 6：Session History（最近 N 条对话消息）
```

只要遵守 [`host-decoupling.md`](host-decoupling.md) 的解耦字段，你的领域词、调度规则、UI 标签、事件订阅都会被 Host 在正确的 Layer 自动拼装到 prompt 中。

---

## 5. 全流程范例：从用户输入到 widget 渲染

参考实现：「列出所有菜谱 → 用户点卡片 → 进入菜谱 canvas」对应的事件流：

```
用户在主对话输入"列出所有菜谱"
  ↓
POST /api/chat { session_id: "main", message: "列出所有菜谱" }
  ↓
host: SkillRouter (Loop A) ──no_skill_matches──→ Executor (Loop B) flat tools
  ↓                                              LLM 看到 recipe_list_view tool
  ↓                                              ← prompt_contributions 引导
  ↓
SSE: { type:"tool_call", tool:"recipe_list_view", args:{} }
  ↓
host → POST <recipe-one>/api/tools/recipe_list_view {args:{}, _context:{...}}
  ↓
AIPP 返回 { ok:true, html_widget:{widget_type:"recipe-list",title:"菜谱",data:{...}} }
  ↓
host: extractEvents 检测到 html_widget → 跳过 canvas 分支
  ↓
SSE: { type:"html_widget", content:"{widget_type,title,data}" }
SSE: { type:"done" }
  ↓
前端按 widget_type 动态 import ES module 并 mount 卡片
  ↓
用户点卡片里的"番茄炒蛋" → widget:
  hostApi.callTool("recipe_open", {id:"r-001"})
  ↓
前端 → POST /api/apps/recipe-one/open {tool_name:"recipe_open", tool_args:{id:"r-001"}}
  ↓
host → POST <recipe-one>/api/tools/recipe_open
  ↓
AIPP 返回 { ok:true, session_id:"r-001", session_name:"番茄炒蛋", graph:{...} }
  ↓
host: output_widget_rules.force_canvas_when=["graph","session_id"] 都非空 → canvas 模式
  ↓
SSE: { type:"session", content:"{name,type:'app',canvas_session_id:'r-001',widget_type:'recipe-board'}" }
SSE: { type:"canvas",  content:"{action:'replace',widget_type:'recipe-board',session_id:'r-001',data:{graph:...}}" }
  ↓
前端切到 canvas 模式，动态 import /widgets/recipe-board/recipe-board.js 并 mount
  ↓
widget 渲染图谱
```

→ **AIPP 端只做了两件事**：
1. 实现 `POST /api/tools/recipe_list_view` 返回 `html_widget:{widget_type,title,data}`，并在对应 widget 里调用 `hostApi.callTool(...)`
2. 实现 `POST /api/tools/recipe_open` 返回带 `graph` + `session_id` 的 JSON，并在该 tool 上声明 `output_widget_rules.force_canvas_when=["graph","session_id"]` + `default_widget="recipe-board"`

其余都是 host 自动处理。**没有任何 AIPP 特定代码进入 host**。

---

## Related

- [`tool-responses.md`](tool-responses.md) — 响应 envelope 与优先级
- [`widgets.md`](widgets.md) — ESM mount / `hostApi`
- [`sessions.md`](sessions.md) — `session` ChatEvent 的来源字段
- [`host-decoupling.md`](host-decoupling.md) — prompt_contributions 等解耦字段
