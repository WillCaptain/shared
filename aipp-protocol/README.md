# AIPP Protocol — AI Plugin Program 协议规范

> 版本：1.6  
> 最后更新：2026-04

---

## 概述

**AIPP（AI Plugin Program）** 是一种让任意 AI Agent（如 World One）发现、调用、渲染独立运行的 AI 应用的开放协议。

```
LLM / Agent (world-one)
    ↕  GET  /api/skills     ← 发现能力（Skill 三层定义）
    ↕  GET  /api/widgets    ← 发现交互方式（Widget Manifest）
    ↕  POST /api/tools/{n}  ← Widget 直接调用工具（不经 LLM）
AIPP App（world-entitir、memory-one 等独立进程）
    ↓  内部调用
AIP 层（纯能力，无 UI 概念）
```

### AIPP vs AIP

| 层次 | 模块 | 职责 | 含 UI 概念？ |
|------|------|------|------------|
| **AIP** | `aip/` | 原子能力（工具），任意 LLM 可调用 | ❌ |
| **AIPP App** | `world-entitir/`、`memory-one/` 等 | 将 AIP 工具组合为 Skill，声明 Widget 绑定 | ✅ |
| **AIPP Agent** | `world-one/` | 发现 AIPP 应用，路由交互 | ✅ |

---

## 一、Skill 三层定义

AIPP Skill 采用**三层叠加**设计，兼容现有 LLM 标准同时扩展 AIPP 专有能力：

```
┌────────────────────────────────────────────┐
│  Layer 3：AIPP 扩展层（canvas + session）    │ ← world-one 消费
├────────────────────────────────────────────┤
│  Layer 2：Mini-agent 层（prompt + tools）    │ ← Agent 编排执行
├────────────────────────────────────────────┤
│  Layer 1：兼容层（name + description +      │ ← OpenAI / Claude 通用
│           parameters）                      │
└────────────────────────────────────────────┘
```

### Layer 1 — 兼容层（OpenAI function-calling 标准）

```json
{
  "name": "world_design",
  "description": "设计本体世界（新建或继续编辑）...",
  "parameters": {
    "type": "object",
    "properties": {
      "name":        { "type": "string", "description": "世界名称（优先使用；只在 active 世界中模糊匹配）" },
      "session_id":  { "type": "string", "description": "已有会话 ID（仅限确认仍有效时使用）" },
      "create_new":  { "type": "boolean", "description": "用户确认新建后再传 true（见 not_found 约定）" }
    },
    "required": []
  }
}
```

### Layer 2 — Mini-agent 执行层（prompt + tools）

借鉴 MCP prompt primitives，使 Skill 成为**可自述的 mini-agent**：

```json
{
  "prompt": "根据输入参数决定路径：\n- 有 session_id → 执行 world_get_design\n- 有 name → 执行 world_create_session",
  "tools":     ["world_create_session", "world_get_design"],
  "resources": ["world_registry"]
}
```

| 字段 | 必选 | 说明 |
|------|------|------|
| `prompt` | ✅ | Skill 执行指令（相当于 mini-agent 的 system prompt） |
| `tools` | ✅ | 依赖的原子 AIP tool 列表（声明性，可为空数组） |
| `resources` | ❌ | 可读取的数据源（如 `world_registry`） |

### Layer 3 — AIPP 扩展层（canvas + session）

```json
{
  "canvas": {
    "triggers":    true,
    "widget_type": "entity-graph"
  },
  "session": {
    "creates_on": "name",
    "loads_on":   "session_id"
  }
}
```

| `canvas.triggers` | world-one 行为 |
|-------------------|---------------|
| `true`  | 默认：Skill 执行后根据 `renders_output_of_skill` 等规则生成 `canvas open/replace`；**若本响应 JSON 根节点含 `html_widget`，则优先走内嵌卡片路径，本轮不根据本响应发 canvas**（见下文「响应字段优先级」） |
| `false` | 保持 Chat Mode，LLM 生成自然语言回复 |

### 完整 Skill 示例

```json
{
  "name": "world_design",
  "description": "设计本体世界（新建或继续编辑）",
  "parameters": {
    "type": "object",
    "properties": {
      "name":        { "type": "string" },
      "session_id":  { "type": "string" },
      "create_new":  { "type": "boolean" }
    },
    "required": []
  },
  "prompt": "有 session_id → 打开；仅有 name → 可能返回 html_widget 消歧或 not_found；用户确认新建后 name + create_new=true。",
  "tools":     ["world_create_session", "world_get_design"],
  "resources": ["world_registry"],
  "canvas":  { "triggers": true, "widget_type": "entity-graph" },
  "session": { "creates_on": "name", "loads_on": "session_id" }
}
```

---

## 二、HTTP 接口规范

### GET /api/skills

```json
{
  "app":     "world",
  "version": "1.0",
  "system_prompt": "（可选）注入 world-one Layer 1 system prompt 的贡献片段",
  "skills": [ { /* skill 对象，含三层 */ } ]
}
```

### GET /api/widgets

```json
{
  "app":     "world",
  "version": "1.0",
  "widgets": [ { /* widget manifest 对象 */ } ]
}
```

### GET /api/app（v1.3 新增）

返回应用身份信息，供 Host Apps 启动面板展示：

```json
{
  "app_id":          "memory-one",
  "app_name":        "记忆管理",
  "app_icon":        "<svg ...>...</svg>",
  "app_description": "管理 AI Agent 的长期记忆",
  "app_color":       "#7c6ff7",
  "is_active":       true,
  "version":         "1.0"
}
```

### POST /api/tools/{name}

请求体（由 world-one 自动注入）：

```json
{
  "args":     { /* 工具参数 */ },
  "_context": {
    "userId":         "user-id",
    "sessionId":      "agent-session-id",
    "workspaceId":    "canvas-session-id（若在 canvas 模式）",
    "workspaceTitle": "canvas 会话名称",
    "agentId":        "worldone"
  }
}
```

#### 响应字段优先级（`html_widget` 与 `canvas`）

Host（world-one）解析工具返回 JSON 时约定：

1. **若根节点存在 `html_widget`**：只处理内嵌卡片（Chat 流 iframe），**不**根据**同一次**响应生成 `canvas` / `session` 导航事件；本轮通常结束 LLM 续写，避免文字盖住卡片。
2. **否则**：再按 Skill 的 `canvas.triggers`、`session` 扩展与响应中的 `session_id` / `graph` 等字段生成 canvas 与 session 事件。

因此，**声明了 `canvas.triggers: true` 的应用**仍可在「消歧、列表选择」等场景先返回 `html_widget`；用户点击卡片内动作后，再由后续工具调用返回可驱动 canvas 的数据。

#### `not_found` 响应约定

工具在以下场景返回 `not_found: true`，由 LLM 向用户转述 `message`：

| 场景 | message 示例 |
|------|-------------|
| 完全无匹配 | "未找到名为「X」的世界。如需新建，请告知用户确认。" |
| 命中已归档/失效资源 | "名为「X」的世界已失效/归档，无法编辑。" |
| session_id 无效 | "该世界已被归档，无法编辑。" |

- Host 将完整 JSON 作为 tool 结果交给 LLM，由 LLM 向用户转述 `message`。
- **不得**在未确认时静默创建资源；确认后由 LLM 使用应用约定的布尔参数（如 `create_new`）再次调用。

响应（可选含 canvas 字段）：

```json
{
  "ok":     true,
  "canvas": {
    "action":      "open | patch | replace | close",
    "widget_type": "entity-graph",
    "session_id":  "abc123",
    "data":        { /* widget 渲染数据 */ }
  }
}
```

---

## 三、Widget Manifest 完整规范

Widget Manifest 是 AIPP 协议中最复杂的部分，包含多个可选字段。

### 3.1 基础字段

| 字段 | 必选 | 说明 |
|------|------|------|
| `type` | ✅ | 全局唯一标识符（如 `entity-graph`、`memory-manager`） |
| `source` | ✅ | `builtin` / `url` / `iframe` |
| `description` | ✅ | 人类可读描述，world-one 截取前缀作 session 名称 |
| `renders_output_of_skill` | 推荐 | 声明该 Widget 渲染哪个 Skill 的输出 |
| `welcome_message` | 推荐 | 进入 canvas session 时展示给用户的欢迎语 |
| `context_prompt` | 推荐 | 进入 canvas 模式时追加到 LLM system prompt 的领域上下文 |
| `internal_tools` | 推荐 | Widget UI 通过 ToolProxy 直接调用的工具名列表（不经 LLM） |
| `canvas_skill` | 推荐 | Canvas 模式下 LLM 可调用的工具集定义 |

### 3.2 Disable & Theme 契约（`supports`）

声明此 Widget 支持的能力边界，`AippWidgetSpec` 会验证此字段：

```json
"supports": {
  "disable": true,
  "theme": ["background", "surface", "text", "textDim",
            "border", "accent", "font", "fontSize", "radius", "language"]
}
```

**Disable 契约**：`disable: true` 表示：
- 变更类工具（create/update/delete）在 disabled 状态下必须返回 `{"ok": false, "error": "widget_disabled"}`
- 只读类工具（query/view）不受影响

**Theme 契约**：`theme` 数组声明支持的 CSS 主题变量，world-one 通过以下方式注入：

```javascript
// world-one 前端注入主题（对应 AippWidgetTheme CSS 变量）
containerEl.style.setProperty('--aipp-bg',        '#0a0b10');
containerEl.style.setProperty('--aipp-surface',   '#13151f');
containerEl.style.setProperty('--aipp-text',      '#d0d8f0');
containerEl.style.setProperty('--aipp-text-dim',  '#6b7a9e');
containerEl.style.setProperty('--aipp-border',    '#272b3e');
containerEl.style.setProperty('--aipp-accent',    '#7c6ff7');
containerEl.style.setProperty('--aipp-font',      'system-ui');
containerEl.style.setProperty('--aipp-font-size', '13px');
containerEl.style.setProperty('--aipp-radius',    '8px');
containerEl.dataset.aippLanguage = 'zh';
```

Widget 前端直接用 `var(--aipp-bg)` 等变量即可，无需感知 host 的主题系统。

### 3.3 Widget View 协议（`views` / `refresh_skill` / `mutating_tools`）

这是 AIPP 1.2 新增的**通用 UI 上下文注入机制**。

#### 问题
Widget 有多个视图（如 Memory Manager 有"全部/事实/关系图谱"等 Tab），当用户在"关系图谱"视图发消息时，LLM 不知道当前视图，无法给出针对性指令。

#### 方案
Widget 通过 manifest 的 `views` 字段**自描述**每个视图的 LLM 上下文指令；前端通过 `aippReportView()` 上报当前视图；world-one 在每次 LLM 调用时自动注入对应提示词。

```json
"views": [
  {
    "id":       "ALL",
    "label":    "全部记忆",
    "llm_hint": "用户正在查看所有类型的记忆列表。如修改了任何记忆，操作后请调用 {refresh_skill} 刷新展示。"
  },
  {
    "id":       "RELATION",
    "label":    "关系图谱",
    "llm_hint": "用户正在查看实体关系图谱。实体合并时创建 IS_SAME_AS 谓词的 RELATION 记忆，完成后必须调用 {refresh_skill} 刷新图谱。"
  }
],
"refresh_skill":  "memory_view",
"mutating_tools": ["memory_create", "memory_update", "memory_delete", "memory_supersede", "memory_promote"]
```

| 字段 | 说明 |
|------|------|
| `views[].id` | 视图唯一标识，与前端 `aippReportView(widgetType, viewId)` 中的 `viewId` 对应 |
| `views[].label` | 人类可读标签（用于日志和调试） |
| `views[].llm_hint` | LLM 上下文指令；`{refresh_skill}` 占位符由 AppRegistry 自动替换 |
| `refresh_skill` | 变更后用于刷新 widget 数据展示的 skill 名称 |
| `mutating_tools` | 会改变 widget 数据的工具名列表；world-one 检测到这些工具被调用后自动触发兜底刷新 |

#### 前端集成（Widget 实现方）

```javascript
// 用户切换 Tab 时上报当前视图
function onTabChange(viewId) {
  aippReportView('memory-manager', viewId);  // 全局函数，由 world-one 注入
}
```

`aippReportView()` 是 world-one 注入的全局函数，Widget 调用后，下次用户发消息时 world-one 会将对应的 `llm_hint` 注入 LLM 的最高优先级 system prompt。

#### 数据流

```
用户切换到"关系图谱"Tab
    ↓
aippReportView('memory-manager', 'RELATION')
    ↓ (存入 _aippActiveViews map)
用户发消息："will 和用户是同一个人"
    ↓
sendMessage() 携带 { widget_view: { widget_type: "memory-manager", view_id: "RELATION" } }
    ↓
WorldOneChatController → registry.buildUiHints("memory-manager", "RELATION")
    ↓ (查 views[RELATION].llm_hint，替换 {refresh_skill})
GenericAgentLoop.contextWindow() 注入"🔴 当前 UI 上下文（最高优先级）"
    ↓
LLM 执行 IS_SAME_AS 合并 + 调用 memory_view 刷新
    ↓ (如果 LLM 未主动调用 memory_view)
autoRefreshIfNeeded() → 检测到 mutating_tool 被调用 → 自动补调 memory_view
```

---

## 四、inject_context 协议

Skill 可以声明需要 world-one 自动注入的上下文信息：

```json
"inject_context": {
  "request_context": true,
  "turn_messages":   true
}
```

| 字段 | 效果 |
|------|------|
| `request_context: true` | world-one 注入 `_context`（userId, sessionId, workspaceId, agentId） |
| `turn_messages: true` | world-one 额外注入完整本轮消息列表（如 memory_consolidate 需要理解对话内容） |

---

## 五、memory_hints 协议

Skill 可以声明执行后 Memory Agent 应关注的信息：

```json
"memory_hints": "关注用户的记忆偏好和操作习惯，记录为 PROCEDURAL 类型。"
```

world-one 收集所有 app 的 `memory_hints`，注入 Memory Agent system prompt。

---

## 六、LLM Context 三层架构

world-one 每轮对话的 system prompt 由三层叠加：

```
Layer 0（最高优先级）：本轮 UI 上下文（来自 widget_view → AppRegistry.buildUiHints()）
    → 用户当前所在的 widget 视图的 llm_hint
    → mutating_tools 刷新提醒
    ↓
Layer 1：全局 system prompt（所有 session 共享）
    → world-one 铁律（必须调工具、不得假装完成等）
    → 各 AIPP App 的 system_prompt 贡献片段
    ↓
Layer 2：session entry prompt（task/event session 专有）
    → 简洁回复规范
    ↓
Layer 3：widget context prompt（canvas 激活期间）
    → 来自 widget manifest 的 context_prompt 字段
    → 领域知识（entity 格式规范、设计原则等）
```

---

## 七、合规验证工具

`aipp-protocol` 模块提供两组验证工具类：

### AippAppSpec — Skill & Widget 结构验证

```java
AippAppSpec spec = new AippAppSpec();

// Skill 三层结构完整性验证
spec.assertValidSkillsApiStructure(skillsNode);     // Layer 1 + 2 + 3 结构
spec.assertValidSkillLayer2(skill);                 // prompt + tools 字段
spec.assertValidSkillSessionExtension(skill);       // session 扩展字段

// Widget 结构验证
spec.assertValidWidgetsApiStructure(widgetsNode);   // 基础结构

// 跨接口一致性
spec.assertWidgetTypesRegistered(skillsNode, widgetsNode);
```

### AippWidgetSpec — Widget 契约验证

```java
AippWidgetSpec spec = new AippWidgetSpec();

// Disable 契约
spec.assertWidgetSupportsDisable(widget);
spec.assertMutatingToolBlockedWhenDisabled("memory_create", mutateResp);
spec.assertReadToolWorksWhenDisabled("memory_view", viewResp);

// Theme 契约
spec.assertWidgetThemeCoversProperties(widget, "background", "font", "language");
spec.assertThemeCssVarsComplete(cssVarsNode);
spec.assertThemeColorsAreValidHex(theme);

// View 协议
spec.assertWidgetDeclaresViews(widget);
spec.assertWidgetDeclaresRefreshSkill(widget);
spec.assertWidgetDeclareMutatingTools(widget);
spec.assertWidgetHasViews(widget, "ALL", "RELATION");
```

### AippWidgetTheme — 主题预设

```java
// 内置预设（可直接用于测试和默认值）
AippWidgetTheme dark  = AippWidgetTheme.darkDefault();
AippWidgetTheme light = AippWidgetTheme.lightDefault();

// 转为 CSS 变量 Map（供前端注入）
Map<String, String> cssVars = dark.toCssVars();
// { "--aipp-bg": "#0a0b10", "--aipp-surface": "#13151f", ... }
```

---

## 八、完整 Widget Manifest 示例

以 `memory-manager` 为例（完整的合规 manifest）：

```json
{
  "type":                    "memory-manager",
  "description":             "Memory 管理面板：查看、编辑、删除、提升 Agent 的所有记忆。",
  "source":                  "external",
  "renders_output_of_skill": "memory_view",
  "welcome_message":         "记忆管理面板已打开。你可以查看所有记忆，也可以直接告诉我修改或删除某条。",

  "internal_tools": [
    "memory_query", "memory_create", "memory_update",
    "memory_supersede", "memory_delete", "memory_promote"
  ],

  "canvas_skill": {
    "prompt": "当前在 Memory 管理 Canvas 中。用 memory_query 查询，memory_update 修改，memory_delete 删除。",
    "tools":  ["memory_query", "memory_create", "memory_update", "memory_delete", "memory_promote"]
  },

  "context_prompt": "用户正在查看和管理 Agent 的 Memory。使用 memory_query/update/delete/promote/create 工具操作记忆。",

  "supports": {
    "disable": true,
    "theme":   ["background", "surface", "text", "textDim", "border", "accent",
                "font", "fontSize", "radius", "language"]
  },

  "views": [
    {
      "id":       "ALL",
      "label":    "全部记忆",
      "llm_hint": "用户正在查看所有类型的记忆列表。如修改了任何记忆，操作后请调用 {refresh_skill} 刷新展示。"
    },
    {
      "id":       "RELATION",
      "label":    "关系图谱",
      "llm_hint": "用户正在查看实体关系图谱。实体合并时创建 IS_SAME_AS 谓词的 RELATION 记忆，完成后必须调用 {refresh_skill} 刷新图谱。"
    }
  ],
  "refresh_skill":  "memory_view",
  "mutating_tools": ["memory_create", "memory_update", "memory_delete", "memory_supersede", "memory_promote"]
}
```

---

## 九、Session 类型规范（v1.4 新增）

world-one 的 UiSession 有三种类型，由 `type` 字段区分：

| type | 说明 | 在 Task Panel 显示？ | Session ID 格式 | 典型来源 |
|------|------|---------------------|-----------------|----------|
| `conversation` | 主对话 session（每次打开 world-one 自动创建） | ✅（常驻） | `"main"` | 系统初始化 |
| `task` | 用户或 LLM 发起的任务（如进入本体世界） | ✅ | 随机 UUID | Skill 响应含 `new_session` |
| `event` | 外部系统推送的事件，独立 session | ✅ | 随机 UUID | 外部 Event 推送 |
| `app` | AIPP 应用专属 session（如记忆管理） | ❌ 不显示 | `"app-{appId}"` | 从 Apps 面板打开 |

### App Session 协议

**App Session** 是专属于某个 AIPP 应用的持久 session，解决"应用面板污染主对话历史"问题：

- **固定 ID**：`app-{appId}`（如 `app-memory-one`），不使用随机 UUID
- **幂等创建**：world-one 启动时自动确保所有已注册 app 的 app session 存在
- **不在 Task Panel 显示**：`GET /api/sessions` 不包含 `app` 类型 session
- **独立上下文**：LLM 的对话历史、工具调用、canvas 状态全部存储在该 session，不污染主 session

### Skill 声明 App Session（支持 1-N）

Skill 的 Layer 3 扩展层新增 `session_type` 字段：

```json
{
  "name": "memory_view",
  "session": {
    "session_type": "app",
    "app_id":       "memory-one"
  }
}
```

```json
{
  "name": "world_design",
  "session": {
    "session_type": "app",
    "app_id":       "world",
    "creates_on":   "name",
    "loads_on":     "session_id"
  }
}
```

### App Session 路由键（自然 1-N）

world-one **不规定**每个 app 只能有 1 个或必须有 N 个 session。  
是否单实例或多实例由 skill 响应是否携带 `session_id` 自然决定：

- `session_type=app` 且**无** `session_id`：按 `app_id` 路由（单实例 app session）
- `session_type=app` 且**有** `session_id`：按 `(app_id, session_id)` 路由（多实例 app session）

示例：
- memory-one 常见为单实例：`app_id=memory-one`，不携带 `session_id`
- world-entitir 常见为多实例：`app_id=world`，`session_id=EAI|HR|...`

Skill 响应体也可以携带该字段，world-one 据此路由到对应 App Session：

```json
{
  "ok":           true,
  "session_type": "app",
  "app_id":       "memory-one",
  "session_name": "记忆管理",
  "graph":        { "memories": [...] }
}
```

```json
{
  "ok":           true,
  "session_type": "app",
  "app_id":       "world",
  "session_id":   "HR",
  "session_name": "HR World",
  "graph":        { "nodes": [...], "edges": [...] }
}
```

### Session 归一（不嵌套）原则

在某个已命中的 new-session widget 上下文中，再触发另一个 new-session widget 时：

- **不创建**新的 UI session
- 归一到当前 session
- 视图层仅执行 `canvas.open/replace` 覆盖（可返回）

该原则保证 LLM 上下文单一、Task Panel 不爆炸、导航行为可预测。

### 事件流

```
Apps 面板 → 点击 memory-one 应用图标
    ↓
POST /api/apps/memory-one/open
    ↓  (GenericAgentLoop.openApp → memory_view → extractEvents)
SESSION event { type: "app", app_id: "memory-one", ui_session_id: "app-memory-one" }
CANVAS  event { action: "open", widget_type: "memory-manager", ... }
    ↓  (enrichSessionEvent 幂等 ensureApp)
UiSession { id: "app-memory-one", type: "app" } 确保存在
    ↓  (前端 handleSessionEvent, type=app)
switchSession("app-memory-one") — 不打开 Task Panel，不设 activeCategory
```

---

## 十、合规规则速查

| 层 / 字段 | 必选 | 要求 |
|-----------|------|------|
| `GET /api/skills` 顶层 | `app`, `version`, `skills` | 必须 |
| `GET /api/widgets` 顶层 | `app`, `version`, `widgets` | 必须 |
| **`GET /api/app` 顶层** | `app_id`, `app_name`, `app_icon`, `app_description`, `app_color`, `is_active`, `version` | 推荐（Apps 面板展示用） |
| Skill Layer 1 | `name` (snake_case), `description`, `parameters` | 必须 |
| Skill Layer 1 | `canvas` (含 `triggers` boolean) | 必须 |
| Skill Layer 2 | `prompt` (非空), `tools` (数组) | 必须 |
| Skill Layer 3 | `session` | 可选；含 `creates_on`、`loads_on` 或 `session_type`/`app_id` |
| Widget | `type`, `source`, `description` | 必须 |
| **Widget** | **`app_id`（所属 app）** | **推荐（Apps 面板关联用）** |
| **Widget** | **`is_main: true/false`（是否为 app 主界面）** | **必须（每个 app 恰好一个）** |
| **Widget** | **`is_canvas_mode: true/false`（展示模式）** | **推荐（false = html_widget 内嵌）** |
| `html_widget.html`   | ✅ | html_widget 工具响应必须含 HTML 字符串 |
| `html_widget.height` | ✅ | html_widget iframe 初始高度（如 `"300px"`） |
| **`html_widget.title`** | **✅** | **widget 内容标题（Host 用于"已处理"卡片定语等场景）** |
| Widget | `supports.disable: true` | 若声明 disable |
| Widget | `supports.theme` (数组，值来自 AippWidgetTheme 字段名) | 若声明 theme |
| Widget | `views[].id`, `views[].label`, `views[].llm_hint` | 若声明 views |
| Widget | `refresh_skill` (非空字符串) | 若声明 views |
| Widget | `mutating_tools` (非空数组) | 若声明 views |
| **工具 JSON** | **`html_widget` 与 `graph`/canvas 二选一（同次响应）** | 同次响应含 `html_widget` 时 Host 不根据该响应发 canvas（见 §POST /api/tools 响应优先级） |

---

## 十一、App Identity 协议（v1.3 新增）

### GET /api/app — 应用身份信息

AIPP v1.3 新增 `GET /api/app` 端点，供 host（world-one）的 **Apps 启动面板** 读取展示信息：

```json
{
  "app_id":          "memory-one",
  "app_name":        "记忆管理",
  "app_icon":        "<svg viewBox='0 0 24 24'>...</svg>",
  "app_description": "管理 AI Agent 的长期记忆（事实、目标、事件、关系）",
  "app_color":       "#7c6ff7",
  "is_active":       true,
  "version":         "1.0"
}
```

| 字段 | 说明 |
|------|------|
| `app_id` | 唯一标识符（kebab-case），必须与 `/api/skills.app` 一致 |
| `app_name` | 显示名称（中文或本地化） |
| `app_icon` | SVG inline 字符串或公网 URL（推荐 inline SVG） |
| `app_description` | 一行描述（用于 tooltip 或 Apps 面板副标题） |
| `app_color` | 主题色 hex（Apps 面板卡片着色） |
| `is_active` | false 时 Host 在 Apps 面板中置灰此 app |
| `version` | 版本号（纯展示） |

### Widget App Identity 字段

`/api/widgets` 的每个 widget 对象新增三个字段：

```json
{
  "type":           "memory-manager",
  "app_id":         "memory-one",
  "is_main":        true,
  "is_canvas_mode": true,
  ...
}
```

| 字段 | 说明 |
|------|------|
| `app_id` | 所属 AIPP 应用 ID（与 `/api/app.app_id` 一致） |
| `is_main` | `true` = 此 widget 是该 app 的主界面；**每个 app 必须恰好声明一个**（从 Apps 面板点击的入口）|
| `is_canvas_mode` | `true` = Canvas 模式（全屏）；`false` = Chat 内嵌 html_widget 卡片模式 |

### html_widget 响应格式（is_canvas_mode=false）

`is_canvas_mode: false` 的 widget 工具响应使用 `html_widget` 字段代替 `canvas`：

```json
{
  "html_widget": {
    "html":   "<div class='card'><h2>统计摘要</h2><p>共 42 条记忆</p></div>",
    "height": "300px",
    "title":  "统计摘要"
  }
}
```

| 字段 | 必选 | 说明 |
|------|------|------|
| `html` | ✅ | 完整 HTML 片段，由 Host 注入 `iframe[srcdoc]`，CSS 与主 DOM 完全隔离 |
| `height` | ✅ | iframe 初始高度（如 `"300px"`），可随内容自适应 |
| `title` | ✅ | **widget 的人类可读标题**（如 `"应用列表"`、`"世界列表"`）。<br>Host 在以下场景使用：①聊天历史中的"已处理"卡片显示定语（`{title} · 已在界面上打开`）；②调试日志与会话记录 |

**`title` 设计规范：**
- 简短（2-8 字），代表 widget 内容的名词短语
- 建议与 `/api/app.app_name` 或 widget manifest 的 `description` 保持语义一致
- 不需要包含动词（"查看"、"打开" 由 Host 拼接）

Host（world-one）将 `html_widget` 内容以 `iframe[srcdoc]` 嵌入聊天消息流，CSS 完全隔离。

**更新逻辑**：
- 若最近一条聊天消息已经是该 widget 的 iframe 卡片 → **替换**（刷新 srcdoc）
- 否则 → **追加**新消息

### Apps 启动面板（world-one 集成）

world-one 左侧 function bar 新增 **Apps** 按钮（apps图标），点击展开 Apps 面板：

```
GET /api/apps   → 返回所有已注册 app 的 manifest 列表（含 main_widget_type）
POST /api/apps/{appId}/open  → 绕过 LLM 直接打开 app 主 widget，SSE 流式返回事件
```

点击 app 图标的行为与从 chatbot 触发完全一致（遵循"只关注是否有 new_session"原则）：
- Skill 响应含 `new_session` → 在 Task Panel 创建 task
- Skill 响应不含 `new_session` → 直接打开 Canvas（不产生 task）

### 合规验证

```java
AippAppSpec spec = new AippAppSpec();

// /api/app 结构验证
spec.assertValidAppManifest(appNode);

// app_id 跨接口一致性
spec.assertAppIdConsistency(appNode, skillsNode);

// widget App Identity 字段验证
spec.assertWidgetsHaveAppIdentityFields(widgetsNode);
spec.assertExactlyOneMainWidget(widgetsNode, appIds); // 每个 app 必须恰好有一个主 widget

// Widget 维度
AippWidgetSpec wspec = new AippWidgetSpec();
wspec.assertWidgetHasFullAppIdentity(widget);     // app_id + is_main + is_canvas_mode
wspec.assertHtmlWidgetResponse("tool", response); // is_canvas_mode=false 时的响应格式
```
