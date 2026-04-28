# AIPP Protocol — AI Plugin Program 协议规范

> 版本：2.1
> 最后更新：2026-04
> 受众：**LLM / 开发者**。把本文整个喂给 LLM，它就能编写一个合规的 AIPP 应用。

---

## 0. 阅读指南（给 LLM 的）

你要做的事是：**实现一个独立 HTTP 服务，让任意 AIPP Agent（Host）能发现你、调用你的能力、挂载你的 UI**。

AIPP 把"能力"分成清晰的三类，分别对应不同的端点和受众：

```
┌──────────┬───────────────────────────┬────────────────────────────────────┐
│ 类别     │ 给谁                       │ 端点                                │
├──────────┼───────────────────────────┼────────────────────────────────────┤
│ Tool     │ LLM / Widget UI / Host    │ GET  /api/tools                     │
│          │ 直接调用                   │ POST /api/tools/{name}              │
│          │ （原子 function-call）     │                                     │
├──────────┼───────────────────────────┼────────────────────────────────────┤
│ Skill    │ LLM 渐进式发现              │ GET  /api/skills（轻索引）           │
│          │ （多步事务说明书）          │ GET  /api/skills/{id}/playbook       │
│          │                           │      （SKILL.md 正文，按需加载）     │
├──────────┼───────────────────────────┼────────────────────────────────────┤
│ Widget   │ Host 挂载到 UI             │ GET /api/widgets                    │
│          │ （展示和交互）              │                                     │
└──────────┴───────────────────────────┴────────────────────────────────────┘
```

**铁律**：
- 一切**能力**通过 `tools` 暴露
- 一切**多步流程**通过 `skills` 暴露（progressive disclosure，不全量进 LLM context）
- 一切**展示**通过 `widgets` 暴露

读完本文你要掌握 5 件事：
1. 4 个 HTTP 端点（§2）
2. **Tool**：原子能力，对 LLM 单次 call → 单次 response 的黑盒（§3）
3. **Skill**：渐进式发现的多步说明书（§4）— ★ 容易漏看，重点
4. **Widget**：UI 组件 manifest（§5）
5. **Host 解耦原则**：6 个 manifest 字段让 Host 不需要为你写任何特判代码（§6）

直接看 §1 Quickstart 抄一份骨架，再按需要扩展。

> **写完怎么自查**：把响应 JSON 喂给 `aipp-protocol` 模块的 `AippAppSpec` / `AippWidgetSpec` 的 `assert*` 方法（§15）。这是协议**最终事实**——文档与方法不一致时，以方法为准。

---

## 1. Quickstart — 一个最小合规 AIPP（5 分钟）

假设你要做一个 `recipe-one` 应用：管理菜谱。

### Step 1：暴露 4 个必需 HTTP 端点

| 端点 | 用途 |
|------|------|
| `GET /api/app` | 应用身份（名字、icon、版本） |
| `GET /api/tools` | 暴露给 LLM 调用的原子能力清单 |
| `GET /api/widgets` | UI 组件清单（每个 app 必须恰好一个 `is_main:true` widget） |
| `POST /api/tools/{name}` | 执行 tool |
| `POST /api/events` | （可选）接收 Host 派发的事件，仅订阅了事件的 app 需要 |

### Step 2：`GET /api/app` 响应

```json
{
  "app_id":          "recipe-one",
  "app_name":        "菜谱管理",
  "app_icon":        "<svg viewBox='0 0 24 24'>...</svg>",
  "app_description": "管理菜谱、食材、烹饪步骤",
  "app_color":       "#ff8a65",
  "is_active":       true,
  "version":         "1.0"
}
```

`app_id` 必须 kebab-case，且必须与 `/api/tools.app` / `/api/widgets.app` 的值一致。

### Step 3：`GET /api/tools` 响应

```json
{
  "app":     "recipe-one",
  "version": "1.0",
  "tools": [
    {
      "name":        "recipe_list",
      "description": "列出菜谱（可按食材/分类筛选）",
      "parameters": {
        "type": "object",
        "properties": {
          "query": { "type": "string", "description": "可选筛选词" }
        },
        "required": []
      },
      "canvas":      { "triggers": false },
      "visibility":  ["llm", "ui"],
      "scope":       { "level": "universal", "owner_app": "recipe-one", "visible_when": "always" },
      "display_label_zh": "菜谱列表"
    }
  ]
}
```

> ⚠️ 不要在 tool entry 里加 `prompt` / `tools[]` / `resources` —— 协议明确禁止（§3 / §4.8）。需要 LLM 多步编排请走 Skill（§4）。

### Step 4：`GET /api/widgets` 响应

```json
{
  "app":     "recipe-one",
  "version": "1.0",
  "widgets": [
    {
      "type":           "recipe-board",
      "app_id":         "recipe-one",
      "is_main":        true,
      "is_canvas_mode": true,
      "source":         "external",
      "render": {
        "kind": "esm",
        "url":  "/widgets/recipe-board/recipe-board.js"
      },
      "description":    "菜谱看板：列表、详情、编辑",
      "supports": {
        "disable": true,
        "theme":   ["background", "surface", "text", "textDim", "border", "accent",
                    "font", "fontSize", "radius", "language"]
      }
    }
  ]
}
```

### Step 5：`POST /api/tools/recipe_list` 实现

请求体由 Host 自动注入（你不必填 `_context`）：

```json
{
  "args":     { "query": "番茄" },
  "_context": { "userId": "u1", "sessionId": "main", "agentId": "<host-agent-id>" }
}
```

响应 — 选 A 或 B：

**A. 嵌入聊天卡片（推荐做"列表/查询"返回）**：
```json
{
  "ok": true,
  "html_widget": {
    "widget_type": "recipe-list",
    "title":       "菜谱列表",
    "data":        { "query": "番茄" }
  }
}
```

**B. 打开 canvas 模式 widget**：
```json
{
  "ok": true,
  "canvas": {
    "action":      "open",
    "widget_type": "recipe-board",
    "data":        { "recipes": [...] }
  }
}
```

### Step 6：（可选）声明 Skill — 多步事务

如果你的 app 有"做一周菜谱"这种**多步流程**（不是一锤子查询），用 Skill 暴露而非 Tool。`GET /api/skills`：

```json
{
  "app":     "recipe-one",
  "version": "1.0",
  "skills": [{
    "name":          "make_weekly_meal_plan",
    "description":   "Plan a 7-day meal schedule from user constraints and pantry. Use when the user asks to \"做一周菜谱 / plan my week / 帮我安排下周吃什么\". Pre-condition: at least 5 recipes in inventory.",
    "allowed_tools": ["recipe_list", "recipe_get", "pantry_query", "calendar_write"],
    "playbook_url":  "/api/skills/make_weekly_meal_plan/playbook",
    "level":         "app",
    "owner_app":     "recipe-one"
  }]
}
```

把 `resources/skills/make_weekly_meal_plan/SKILL.md` 写成 frontmatter + 步骤化 Markdown（详见 §4.5），Host 会做 progressive disclosure 召回 + 加载 + 沙箱执行。**为什么不直接写成 Tool？** 看 §4.1。

### Step 7：注册到 Host

```bash
curl -X POST http://host:8090/api/registry/install \
  -H "Content-Type: application/json" \
  -d '{"app_id":"recipe-one","base_url":"http://localhost:8095"}'
```

完成。Host 会自动从你的 `/api/app`、`/api/tools`、`/api/skills`、`/api/widgets` 拉取所有元数据，**无需任何 host 侧代码改动**。

---

## 2. 协议总览

```
LLM / Agent (Host)
    ↕  GET  /api/app                       ← 应用身份（icon/name/color）
    ↕  GET  /api/tools                     ← 原子 Tool 清单（权威）
    ↕  GET  /api/skills                    ← Skill Playbook 索引（progressive disclosure，可选）
    ↕  GET  /api/skills/{id}/playbook      ← Skill 正文 SKILL.md
    ↕  GET  /api/widgets                   ← Widget Manifest 清单
    ↕  POST /api/tools/{n}                 ← 执行 tool
    ↕  POST /api/events                    ← 接收 Host 派发的事件（仅订阅方需实现）
AIPP App（独立进程）
```

### AIPP vs AIP

| 层 | 模块 | 职责 | 含 UI？ |
|----|------|------|--------|
| **AIP** | 纯能力库 | 原子工具，任意 LLM 可调用 | ❌ |
| **AIPP App** | 你正在写的 | 把 AIP 工具组合为 Skill + 持有自己的 Widget UI | ✅ |
| **AIPP Agent / Host** | 任意实现协议的 Agent | 发现 / 调度 / 挂载 / 路由 | ✅（仅 Host UI） |

---

## 3. Tool — 原子能力（`/api/tools`）

Tool 是 LLM / Widget UI / Host 直接调用的**原子函数**。从 LLM 视角看：**一次 call → 一次 response，全部完事**。Tool 内部允许在服务端 Java/Python 把多个内部 API 串起来，但**对 LLM 是黑盒**——LLM 不参与编排。需要 LLM 参与多步编排的能力，请用 Skill（§4）。

每个 tool entry 由 2 块组成：

```
┌─────────────────────────────────────────────────────────┐
│  AIPP 扩展层（可选）                                       │
│   canvas / session / output_widget_rules /               │
│   runtime_event_callbacks / lifecycle / event_subs /     │
│   display_label_zh                                       │
├─────────────────────────────────────────────────────────┤
│  OpenAI function-calling 兼容层（必选）                    │
│   name / description / parameters                        │
└─────────────────────────────────────────────────────────┘
```

> ⚠️ **历史遗留警告**：早期版本曾有"Layer 2 Mini-agent"层（要求每个 tool 带 `prompt` / `tools[]` / `resources`），现已**彻底废弃**。`AippAppSpec.assertValidSkillStructure` 启动时会拒绝任何带这三个字段的 tool entry。详见 §4.8 的判定准则。

### 3.1 兼容层（必选）

```json
{
  "name": "recipe_get",
  "description": "查询某道菜的详细信息",
  "parameters": {
    "type": "object",
    "properties": {
      "name":       { "type": "string", "description": "菜名" },
      "session_id": { "type": "string", "description": "已有会话 id（仅在确认仍有效时传）" }
    },
    "required": []
  }
}
```

`name` 必须 snake_case；`parameters.type` 必须为 `"object"`，且必须包含 `properties` 与 `required`。

`description` 描述**做什么 + 返回什么**，不要写"调完我之后请继续调 X"——那是 Skill 的事（§4.8）。

### 3.2 AIPP 扩展层（可选）

```json
{
  "canvas":  { "triggers": true, "widget_type": "recipe-board" },
  "session": { "creates_on": "name", "loads_on": "session_id" },

  "output_widget_rules": {
    "force_canvas_when": ["graph", "session_id"],
    "default_widget":    "recipe-board"
  },
  "lifecycle":            "post_turn",
  "runtime_event_callbacks": [
    { "events": ["decision_result"], "path": "/api/recipes/{recipeId}/decision-result" }
  ],
  "event_subscriptions":  ["workspace.changed"],
  "display_label_zh":     "查询菜谱"
}
```

每个字段单独详解见 §6。

### 3.3 Tool 顶层附加字段

`/api/tools` 中每个 tool 必须额外携带：

```json
{
  "visibility": ["llm", "ui"],
  "scope": {
    "level":        "universal | app | widget",
    "owner_app":    "recipe-one",
    "visible_when": "always"
  }
}
```

| 字段 | 含义 |
|------|------|
| `visibility` | `llm` = LLM 可调；`ui` = widget UI 可调（绕过 LLM）；`host` = Host 内部可调 |
| `scope.level` | `universal` = 主对话即可调；`app` = 仅在 app session 内可调；`widget` = 仅在该 widget 内可调 |
| `scope.owner_app` | 必须等于 `app_id` |
| `scope.visible_when` | `always` 或表达式（保留扩展） |

---

## 4. Skill — 渐进式发现的多步说明书（`/api/skills`）

> ★ **如果你只读一节，读这节**。这是 AIPP 区别于"一堆 OpenAI tools"的核心设计。

### 4.1 为什么需要 Skill（与 Tool 的边界）

LLM 同时可见的 Tool 一旦超过 ~30 个，选择质量明显下降。Skill 解决两件事：

1. **Token 节省**：所有 app 的 Tool 全量注入会让 system prompt 线性膨胀。Skill 改为 **progressive disclosure** —— LLM 只看 `name + description`（≤1024 字符），按需加载 SKILL.md 正文。
2. **多步事务约束**：Tool 是一锤子买卖；Skill 是带步骤、前置条件、错误处理的"说明书"。LLM 按 playbook 执行，比自己摸索靠谱得多。

经验法则：

- ✅ Skill：「规划一周菜谱」「批量整理收件箱」「按约束生成报价单」—— 一句话能描述的、需要多个 tool 协作完成的事
- ❌ Skill：「调用 recipe_create」—— 这是 tool，不是 skill
- ❌ Skill：「帮我管理整个项目」—— 太粗，应拆成多个 skill

### 4.2 双轨结构

```
┌──────────────────────┐         ┌────────────────────────┐
│  GET /api/skills     │         │  GET /api/skills/      │
│  返回**轻索引**       │   →    │       {id}/playbook    │
│  4 字段，~200 字节/条  │  按需   │  返回 SKILL.md 正文    │
│                      │  加载   │  text/markdown         │
│  LLM 全量看           │         │  LLM 只在命中后看       │
└──────────────────────┘         └────────────────────────┘
```

### 4.3 `/api/skills` 索引条目（4 字段必选 + 2 字段可选）

```json
{
  "name":          "plan_weekly_menu",
  "description":   "Plan a 7-day meal schedule from user dietary constraints and current pantry contents. Use when the user asks to \"plan my week\", \"做一周菜谱\", \"安排下周吃什么\", or pastes a constraint list (\"低碳水/不吃牛肉/孩子要喝奶\") and wants a full weekly schedule. Reads inventory and recipe library, asks clarifying questions when constraints conflict, then writes the plan to the user calendar. Never creates new recipes — only schedules existing ones. Requires at least 5 recipes in the library.",
  "allowed_tools": ["recipe_list", "recipe_get", "pantry_query", "calendar_write"],
  "playbook_url":  "/api/skills/plan_weekly_menu/playbook",

  "level":         "app",
  "owner_app":     "recipe-one"
}
```

| 字段 | 必选 | 说明 |
|------|------|------|
| `name` | ✅ | 唯一标识，snake_case，与 `playbook_url` 路径一致 |
| `description` | ✅ | **WHAT + WHEN 一段话**，长度 40–1024 字符。这是召回的**唯一**信号 — 写不好等于 LLM 看不到这个 skill |
| `allowed_tools` | ✅ | playbook 执行期允许调用的 tool 名白名单。**非空数组**，且每个名字必须出现在某个 app 的 `/api/tools` 中（host 在 router 阶段做依赖校验，缺失则该 skill 自动从 catalog 移除） |
| `playbook_url` | ✅ | 懒加载 SKILL.md 正文的相对路径，约定 `/api/skills/{name}/playbook` |
| `level` | 推荐 | `app` = app session 内可见；`widget` = 仅在该 widget 打开时可见 |
| `owner_widget` / `owner_app` | 视 `level` 而定 | `level=widget` 时必填 `owner_widget`；`level=app` 时填 `owner_app` |

### 4.4 description 写作规范（lint）

参考实现强 lint 以下规则，违反则启动失败：

| 规则 | 性质 | 说明 |
|------|------|------|
| 长度 ≤ 1024 字符 | ❌ 硬约束 | 超长会让注入 system prompt 时占用过多上下文 |
| 长度 ≥ 40 字符 | ⚠️ 软告警 | 太短的 description 召回信号不足 |
| 必须含 WHEN clause | ⚠️ 软告警 | 检测 `"use when" / "用于" / "当用户" / "when the user" / "when "` 等关键词。**没有 WHEN 子句的 description 等于没用** |
| `allowed_tools` 非空 | ❌ 硬约束 | 空数组的 skill 没有执行能力 |

**好的 description 模板**：
> [WHAT — 一句话说做什么]. **Use when** [WHEN — 用户什么意图、什么场景、什么 widget 内]. [负向边界 — 哪些事**不**做]. [前置条件 — 需要什么状态]。

§4.3 的 `plan_weekly_menu` description 就是范本：动作 + use when + 列出多种触发表达 + 明确负向边界 + 前置条件。

### 4.5 SKILL.md playbook 格式（Anthropic Skills 风格）

`GET /api/skills/{id}/playbook` 返回 `text/markdown;charset=UTF-8`，YAML frontmatter + Markdown 正文：

```markdown
---
name: plan_weekly_menu
description: Plan a 7-day meal schedule from user dietary constraints and current pantry contents. Use when the user asks to "plan my week", "做一周菜谱", "安排下周吃什么", or pastes a constraint list and wants a full weekly schedule. Never creates new recipes — only schedules existing ones. Requires at least 5 recipes in the library.
allowed-tools:
  - recipe_list
  - recipe_get
  - pantry_query
  - calendar_write
---

# Plan Weekly Menu

> 根据用户饮食约束 + 当前食材库存，安排未来 7 天的菜谱并写入日历。
> 只调度已有菜谱，不创建新菜谱。

## Pre-conditions
- 菜谱库至少含 5 道菜（不足时停下，提示用户先添加）
- 用户已表达饮食约束（口味、忌口、人数）；不足时本 playbook 第一步会问

## Parameters（从用户输入或上下文解析）
- `week_start` — 起始日期（默认下周一）
- `constraints` — 饮食约束对象（口味、忌口、人数、预算等）
- `target_calendar_id` — 写入的日历 id（由 widget context 自动注入或问用户）

## Procedure

### Step 1 — 收齐约束
1. 检查 `constraints` 是否含口味偏好 + 忌口 + 人数三项；
2. 缺任一项 → 用一句话向用户问齐，**不要默默假设**；
3. 用户拒绝补充 → 停下，告知"约束不足无法规划"。

### Step 2 — 读库存与候选菜谱
1. 调 `pantry_query()` 获取当前食材；
2. 调 `recipe_list(filter=constraints)` 拿到候选；
3. 候选 < 5 → **停下**，告知用户菜谱库不足，建议先 `recipe_create`。

### Step 3 — 编排 7 天
1. 对每一天：从候选中挑一道与库存匹配度最高的；
2. 同一周不重复（除非用户明确要求重复）；
3. 调 `recipe_get(name)` 拿详细配料确认；
4. 任一步骤失败立刻停下汇报，不要静默跳过。

### Step 4 — 写入日历（强制）
所有 7 天编排完成后**必须**调用 `calendar_write(events=[...])` 一次性写入。
即便其中某天用户后来想改，也先完整写入再让用户编辑 —— 不要分散写。

## Don'ts
- ❌ 不要为了凑数推荐库存里没有食材的菜
- ❌ 不要在没有 `constraints` 时硬猜"用户应该喜欢"
- ❌ 不要在 `calendar_write` 前先告诉用户"已安排"，必须以工具返回为准
```

#### Frontmatter 字段约定

| 字段 | 必选 | 说明 |
|------|------|------|
| `name` | ✅ | 必须与 URL 路径中的 `{id}`、索引中的 `name` 一致 |
| `description` | ✅ | 与索引同（重复一份，便于 SKILL.md 文件单独阅读时自洽） |
| `allowed-tools` | ✅ | YAML 数组。**注意是连字符 `allowed-tools`**（Anthropic 原生约定），不是下划线 |

#### 正文写作规范

推荐结构：

1. **顶部一句话 blockquote** — 重申 skill 干什么 + 不干什么
2. **Pre-conditions** — 启动该 skill 必须满足的前提（widget context、数据状态等）
3. **Parameters** — 从用户输入解析的参数列表（哪些 widget 自动注入、哪些必须问用户）
4. **Procedure** — Step 1 / Step 2 / ...，每一步明确：
   - 调哪个 tool 传什么参数
   - 失败如何处理（**失败必须停下汇报，不得静默继续**）
   - 用户介入点（确认、补参）
5. **Constraints / Don'ts** — 反模式列表

### 4.6 Skill 召回机制（Host 视角，AIPP 端无须实现）

Host 的 SkillRouter 工作流：

```
用户 query
    ↓
Router（用便宜模型，e.g. mini）只看 skill 索引（name + description）
    ↓
    ├─ 命中 skill X     → 加载 /api/skills/X/playbook 作 system prompt 追加
    │                   → tools 收窄为 allowed_tools 白名单
    │                   → Executor 按 playbook 执行
    ├─ 命中 universal   → 直接执行该 universal tool（如 app_list_view）
    └─ no_skill_matches → 退化为 flat tools 模式
```

AIPP 端的责任仅是：把 description 写好（含 WHEN clause）、把 allowed_tools 列对、把 SKILL.md 写明步骤。**召回是 Host 的事**，不要在 description 里塞关键词列表来"帮助召回"——纯靠 LLM 读 description 语义。

### 4.7 Skill 与 Tool 字段速对照

| | Tool | Skill |
|---|------|-------|
| 命名风格 | snake_case | snake_case |
| 端点 | `GET /api/tools` | `GET /api/skills` + `GET /api/skills/{id}/playbook` |
| LLM 看见 | 全量 schema（function-calling） | 仅 `name + description`（轻索引） |
| 参数声明 | `parameters`（JSON Schema） | **没有** —— 由 playbook 自己向用户问/从上下文取 |
| 内部依赖 | `tools` 数组（mini-agent 内部依赖） | `allowed_tools` 白名单（执行期沙箱） |
| 触发关键词 | 不需要 | **不要写** —— 纯靠 description |
| 嵌入向量 hint | 不需要 | **不要写** —— 纯靠 description |
| 主体内容 | JSON schema | SKILL.md（文件） |

### 4.8 Tool / Skill 判定准则（唯一标准）

> **编排责任在哪一侧，就归哪一侧。**

| 编排发生在 | 归类 | 原因 |
|------|------|------|
| **Server 端**（AIPP 自己的 Java/Python 代码内部串多个原子调用，对 LLM 是单次 call → 单次 response 完事） | **Tool** | LLM 不需要理解流程；多步细节封装在实现里 |
| **LLM 端**（需要 LLM 读响应、判断 status/分支、按状态机选下一个 tool 调；或需要在过程中向用户问参/确认） | **Skill** | 流程必须显式告诉 LLM；放进 SKILL.md 渐进披露，不要常驻 system prompt |

**典型反模式（必须修正）**：

- ❌ Tool 的 `description` 里写「调完我之后你还得继续调 X」 — 这是把 LLM 编排塞进 tool 描述，强制全量驻留 system prompt。**升级为 Skill**。
- ❌ Tool 响应里返 `next_tool_recommended` 但不在 SKILL.md 里说明何时遵循 — 没有 playbook 兜底，LLM 行为不可预测。**配套写 Skill**。
- ❌ Tool 的 `parameters` 写"如果用户没说就问用户" — 参数收集是 LLM 的事，应在 Skill playbook 里描述。
- ❌ 把 `prompt` / `tools` / `resources` 这种 mini-agent 编排字段挂在 tool entry 上 — Tool 没有"内部 LLM 子调用"的概念，这些字段对 LLM 完全不可见，纯粹噪音，**必须移除**。

**速查表**：

| 场景 | 选择 |
|------|------|
| 单步、明确参数、调一次完成 | Tool |
| 内部要串多个原子调用，但对 LLM 是黑盒（一次响应说完所有结果） | Tool |
| 多步、有前置/校验/分支、需要 LLM 按状态选下一个调用 | Skill |
| 需要在执行中向用户问参或等用户选择卡片 | Skill |
| 频繁失败需要 LLM 决定重试/兜底策略 | Skill |
| 一锤子查询/创建/删除 | Tool |

> 直觉口诀：**LLM 看见就是契约，看不见就是实现**。LLM 看不见的多步串联是 Tool 的实现细节；LLM 看得见的多步流程是 Skill。

---

## 5. Widget Manifest

### 5.1 必需字段

| 字段 | 必选 | 说明 |
|------|------|------|
| `type` | ✅ | 全局唯一标识（如 `recipe-board`），不得使用 `sys.*` 前缀 |
| `app_id` | ✅ | 与 `/api/app.app_id` 一致 |
| `is_main` | ✅ | `true` = app 主入口；**每个 app 必须恰好一个 `is_main:true`** |
| `is_canvas_mode` | ✅ | `true` = 全屏 canvas；`false` = 聊天内嵌 html_widget 卡片 |
| `source` | ✅ | `external`（推荐）/ `builtin` |
| `render` | ✅（非 `sys.*`） | App-owned renderer 声明 |
| `description` | ✅ | 人类可读描述 |

### 5.2 `render` 声明

Host 只能根据本字段挂载 app UI，**不允许内置 app 专属 DOM/JS**。

```json
"render": {
  "kind": "esm",
  "url":  "/widgets/recipe-board/recipe-board.js"
}
```

`url` 可为 absolute 或 app-relative；app-relative 由 Host 用注册时的 `base_url` 解析。

`kind="esm"` 是标准渲染方式：Host 通过动态 `import(url)` 加载 app 提供的 ES module，
并调用 `mount(targetEl, hostApi, data)`。新 AIPP 不应依赖 Host 内置业务 DOM/JS。

ES module widget 必须导出：

```js
export function mount(targetEl, hostApi, data) {}
export function unmount() {}
```

`hostApi` 是 Host 提供给 widget 的唯一通信面：

| 字段/函数 | 说明 |
|-----------|------|
| `callTool(name, args)` | 通过 Host 发起工具调用，适合用户动作产生的新会话/消息 |
| `proxyTool(name, args)` | 直接调用当前 AIPP 工具，适合 widget 内部刷新数据 |
| `sessionId` | 当前 Host 会话 id |
| `workspace` | 当前工作区上下文（若有） |
| `appId` | 提供该 widget 的 AIPP app id |
| `appBaseUrl` | app 注册时的 base URL |
| `appProxyUrl(path)` | 生成 Host 代理后的 app-relative URL |

Widget 不得读取 Host 的业务全局变量或调用 Host 内部函数。需要状态更新时，Host 会把
tool 返回的 `html_widget.data` 或 `canvas` payload 传给 widget。

### 5.3 `supports`：disable + theme 契约

```json
"supports": {
  "disable": true,
  "theme":   ["background", "surface", "text", "textDim", "border",
              "accent", "font", "fontSize", "radius", "language"]
}
```

- `disable: true` → 变更类工具在 disabled 状态下必须返回 `{"ok":false,"error":"widget_disabled"}`；只读类工具不受影响。
- `theme: [...]` → Host 注入对应 CSS 变量 `--aipp-bg / --aipp-surface / ...`；widget 直接 `var(--aipp-bg)` 即可。

### 5.4 Views 协议（多 Tab widget）

```json
"views": [
  {
    "id":       "ALL",
    "label":    "全部",
    "llm_hint": "用户正在查看全部菜谱。如修改了任何菜谱，操作后调用 {refresh_skill} 刷新。"
  }
],
"refresh_skill":  "recipe_view",
"mutating_tools": ["recipe_create", "recipe_update", "recipe_delete"]
```

Widget 前端调用全局函数 `aippReportView(widgetType, viewId)` 上报当前 Tab；Host 在下次 LLM 调用时把对应 `llm_hint` 注入最高优先级 system prompt。`{refresh_skill}` 占位符自动替换。

`mutating_tools` 中任一被调用时，若 LLM 没主动 refresh，Host 自动补调一次 `refresh_skill`。

### 5.5 完整 Widget 示例

```json
{
  "type":                    "recipe-board",
  "app_id":                  "recipe-one",
  "is_main":                 true,
  "is_canvas_mode":          true,
  "source":                  "external",
  "render": {
    "kind": "esm",
    "url":  "/widgets/recipe-board/recipe-board.js"
  },
  "description":             "菜谱管理面板",
  "renders_output_of_skill": "recipe_view",
  "welcome_message":         "菜谱管理已打开。",
  "context_prompt":          "用户正在查看和管理菜谱。使用 recipe_create/update/delete 操作。",
  "supports": {
    "disable": true,
    "theme":   ["background", "surface", "text", "textDim", "border",
                "accent", "font", "fontSize", "radius", "language"]
  },
  "views": [
    { "id": "ALL",     "label": "全部",   "llm_hint": "用户在查看全部菜谱。修改后调用 {refresh_skill}。" },
    { "id": "FAVORITE","label": "收藏",   "llm_hint": "用户在查看收藏菜谱。" }
  ],
  "refresh_skill":  "recipe_view",
  "mutating_tools": ["recipe_create", "recipe_update", "recipe_delete"]
}
```

---

## 6. Host 解耦协议（v2.0 核心）

> **铁律**：Host 不会为任何 AIPP 写一行特判代码。所有特化行为必须通过下列字段在 manifest 中**自描述**。Host 只懂这些字段的语义，不懂你叫什么名字。

### 5.1 `output_widget_rules` — 自描述响应模式

**问题**：tool 响应何时进 canvas、何时返回 html_widget？

**协议**：在 skill 上声明：

```json
"output_widget_rules": {
  "force_canvas_when": ["graph", "session_id"],
  "default_widget":    "recipe-board"
}
```

| 字段 | 含义 |
|------|------|
| `force_canvas_when` | 字符串数组。当响应 JSON 同时包含**所有**这些字段且非空时，Host **强制走 canvas**，即便响应里同时带了 `html_widget` 也优先 canvas。 |
| `default_widget` | 强制 canvas 时的兜底 widget_type（响应未指定 `canvas.widget_type` 时使用） |

无此字段 → Host 走默认规则：响应有 `html_widget` 走卡片，有 `canvas` 走 canvas。

### 5.2 `lifecycle` — 自描述调度时机

**问题**：某些 skill 应该每轮对话结束后自动跑（比如记忆固化）。

**协议**：

```json
"lifecycle": "on_demand | post_turn | pre_turn"
```

| 值 | 含义 |
|----|------|
| `on_demand`（默认） | LLM 主动调用 |
| `post_turn` | 每轮对话结束后 Host **异步**自动调用，不阻塞用户 |
| `pre_turn` | 每轮对话开始前 Host 自动调用（保留扩展） |

`post_turn` skill 通常 `visibility: ["host"]`，不出现在 LLM 工具列表中。

### 5.3 `runtime_event_callbacks` — 自描述运行时事件接收

**问题**：Host 的 ExecutorRouter 拿到 decision 结果 / action resume 等事件时，要往哪发？

**协议（标准形态：数组）**——在 `/api/tools` 顶层声明：

```json
"runtime_event_callbacks": [
  {
    "events": ["decision_result"],
    "path":   "/api/recipes/{recipeId}/decision-result"
  },
  {
    "events": ["action_resume"],
    "path":   "/api/recipes/{recipeId}/resume-action"
  }
]
```

每个元素 `{ events: string[], path: string }`：一组事件共享同一个 callback path。

`{worldId}` / `{recipeId}` 等 path 模板由 Host 用调用上下文中的同名字段替换。Host 收到事件后遍历所有已注册 app，找到 `events[]` 含目标事件名的第一个 callback，POST 过去。

> 兼容旧形态（不推荐新代码使用）：单个 `runtime_event_callback`（单数）`{events:[...], path:"..."}` 对象；以及把 `runtime_event_callbacks` 写成 `{event_name: path}` map。两者 host 仍接受，但新 AIPP 必须使用上面的数组标准形态。

### 5.4 `event_subscriptions` — 自描述事件订阅

**问题**：Host 上工作区切换、用户登录等事件发生时，哪些 app 想收到通知？

**协议**：在 tool 顶层（`/api/tools` 响应根）或单个 tool 上声明：

```json
"event_subscriptions": ["workspace.changed", "user.login"]
```

订阅的 app 必须实现 `POST /api/events`：

```json
// Host → AIPP
{
  "type":      "workspace.changed",
  "payload":   { "workspace_id": "ws-123", "user_id": "u1" },
  "timestamp": "2026-04-28T08:15:00Z"
}
```

返回任意 200 即可，Host 是 fire-and-forget，不等响应。

**Host 已知通用事件**：`workspace.changed`、`user.login`、`session.closed`（陆续扩展）。任何 app 可订阅其中之一或多个。

### 5.5 `display_label_zh` / `display_name` — 自描述 UI 标签

**问题**：tool 在前端的中文显示名（如"创建设计"）应该谁提供？

**协议**：

```json
{
  "name":             "recipe_create",
  "display_label_zh": "新建菜谱",
  "display_name":     "Create Recipe"
}
```

Host 暴露 `GET /api/tool-labels` 聚合所有 app 的标签字典；前端启动时拉取并缓存。**Host 自己不再维护任何 tool 标签清单。**

### 5.6 `prompt_contributions` — 自描述领域提示词

**问题**：你的 AIPP 涉及"菜谱/食材/烹饪"等领域词；Host 不能在它的 system prompt 里写这些（它要保持通用）。怎么让 LLM 知道？

**协议**：在 `/api/tools` 顶层声明：

```json
"prompt_contributions": [
  {
    "layer":    "aap_pre",
    "priority": 100,
    "content":  "【菜谱域】用户提到\"菜/菜谱/食谱/做菜/食材\"等词时，使用 recipe_* 工具；\"列出菜谱\"调 recipe_list，\"做番茄炒蛋\"调 recipe_get(name='番茄炒蛋')。"
  }
]
```

Host 在每轮系统提示中按 layer 顺序聚合所有 app 的贡献：

| layer | 注入位置 |
|-------|---------|
| `aap_pre` | 工具调用前规则（路由提示，最常用） |
| `aap_post` | 工具调用后规则（结果解读） |

排序规则：
- 同 `layer` 内，`priority` 大者靠前；
- `priority` 缺省视为 0；
- 相同 `priority` 内按 app 注册顺序稳定排列；
- 推荐的 `id`（字符串，本 app 内唯一）用于调试日志和未来去重。

> AAP-Post 也可由 tool 响应中的 `aap_hit` 动态激活（按需替换/追加），见 §10.5。

---

## 7. 必备 HTTP 接口规范

### 7.1 `GET /api/app`

| 字段 | 必选 | 说明 |
|------|------|------|
| `app_id` | ✅ | kebab-case，跨端点一致 |
| `app_name` | ✅ | 显示名 |
| `app_icon` | ✅ | 内嵌 SVG 字符串（推荐）或公网 URL |
| `app_description` | ✅ | 一行描述 |
| `app_color` | ✅ | hex 主题色 |
| `is_active` | ✅ | boolean |
| `version` | ✅ | 字符串 |

### 7.2 `GET /api/tools`

```json
{
  "app":     "recipe-one",
  "version": "1.0",
  "system_prompt":         "（可选）注入 Host 系统提示的领域片段（推荐用 prompt_contributions）",
  "prompt_contributions":  [ /* 见 §6.6 */ ],
  "event_subscriptions":   [ /* 见 §6.4 */ ],
  "tools": [ /* tool 对象 */ ]
}
```

### 7.3 `GET /api/skills`（progressive disclosure 索引）

```json
{
  "app":     "recipe-one",
  "version": "1.0",
  "skills": [
    {
      "name":          "make_weekly_meal_plan",
      "description":   "Plan a 7-day meal schedule from user dietary constraints and current pantry. Use when the user asks to \"做一周菜谱 / plan my week / 帮我安排下周吃什么\". Pre-condition: at least 5 recipes in inventory.",
      "allowed_tools": ["recipe_list", "recipe_get", "pantry_query", "calendar_write"],
      "playbook_url":  "/api/skills/make_weekly_meal_plan/playbook",
      "level":         "app",
      "owner_app":     "recipe-one"
    }
  ]
}
```

`skills` 可为空数组（没有 playbook 也合规）；非空则每条必须满足 §4.3 的 4 必选 + 2 推荐字段。

### 7.3.1 `GET /api/skills/{name}/playbook`

返回 `text/markdown;charset=UTF-8`。Frontmatter + 正文，详见 §4.5。未定义返回 HTTP 404。

### 7.4 `GET /api/widgets`

```json
{
  "app":     "recipe-one",
  "version": "1.0",
  "widgets": [ /* widget 对象 */ ]
}
```

### 7.5 `POST /api/tools/{name}`

请求体：

```json
{
  "args":     { /* tool 参数；若客户端没包 args 字段，host 会把整个 body 当作 args 传 */ },
  "_context": {
    "userId":         "user-id",
    "sessionId":      "agent-session-id（GenericAgentLoop 的 id）",
    "workspaceId":    "canvas 内的 session_id（不在 canvas 时可能为 null）",
    "workspaceTitle": "canvas 名（可选）",
    "agentId":        "<host-agent-id>",
    "appBaseUrl":     "http://aipp-host:port（host 在 install 时记录的 AIPP 自身对外地址）",
    "env":            "production | staging | dev（host 注入）"
  }
}
```

| `_context` 字段 | 谁注入 | AIPP 使用场景 |
|---|---|---|
| `userId` | host | 多用户隔离（如按用户作用域读写） |
| `sessionId` | host | 关联到具体对话；写日志、做幂等 |
| `workspaceId` | host | canvas 模式下注入；用于"当前编辑哪个对象" |
| `workspaceTitle` | host | 仅展示用 |
| `agentId` | host | 标识哪个 host agent 在调（多 agent 部署时区分） |
| `appBaseUrl` | host | AIPP 自身对外可达地址，host 在 `/api/registry/install` 时记录；widget 通过 `hostApi.appBaseUrl` / `hostApi.appProxyUrl(path)` 获取资源或调用 app-relative API |
| `env` | host | 让 tool 选对环境（生产/测试），**禁止 AIPP 自行切换 env 重试** |

> ⚠️ `_context` 全部字段视为只读元信息，**不要**塞进业务返回里回传。AIPP 端业务参数走 `args`，元信息走 `_context`。

响应字段优先级（**重要**）：

1. **如果 skill 声明了 `output_widget_rules.force_canvas_when` 且响应中所有列出字段都存在且非空** → Host 走 canvas 模式（即便有 `html_widget` 也忽略）。
2. **否则若响应根含 `html_widget`** → Host 渲染聊天内嵌卡片，不发 canvas，**LLM 不再续写文字**。
3. **否则若响应含 `canvas`** → Host 按 `canvas.action` 处理 widget。
4. **否则**普通 chat 响应。

### 7.6 `POST /api/events`（仅订阅方需实现）

```json
{ "type": "workspace.changed", "payload": { ... }, "timestamp": "..." }
```

---

## 8. 响应约定

### 8.1 `html_widget` 内嵌卡片

```json
{
  "ok": true,
  "html_widget": {
    "widget_type": "recipe-list",
    "title":       "菜谱列表",
    "data":        { "recipes": [] }
  }
}
```

| 字段 | 必选 | 说明 |
|------|------|------|
| `widget_type` | ✅（Plan D） | `/api/widgets` 中注册的 widget type；Host 用其 `render.url` 挂载 ES module |
| `title` | ✅ | 2-8 字短标题（聊天历史"已处理"卡片定语会用）|
| `data` | ✅（Plan D） | 传给 `mount(targetEl, hostApi, data)` 的纯数据 |
更新逻辑：上一条消息已是同 widget → **替换**；否则**追加**。

### 8.2 `canvas` 模式

```json
{
  "ok": true,
  "canvas": {
    "action":      "open | patch | replace | close | inline",
    "widget_type": "recipe-board",
    "session_id":  "abc",
    "data":        { /* widget 渲染数据 */ }
  }
}
```

Host 对 Plan-D canvas widget 的职责只有：解析 widget manifest、挂载 ES module、把 canvas
payload 原样交给 widget。Host 不得理解 app 专属字段（如节点、边、卡片、表格等）。

### 8.3 `not_found` 协定

工具找不到资源时返回：

```json
{ "not_found": true, "message": "未找到名为「番茄炒蛋」的菜谱。如需新建请确认。" }
```

Host 把整个 JSON 交给 LLM 转述。**不得**未确认时静默创建；用户确认后由 LLM 用约定布尔参数（如 `create_new`）再次调用。

### 8.4 `awaiting_confirmation` / `awaiting_selection` 协定

```json
{ "ok": true, "status": "awaiting_confirmation", "html_widget": { /* 确认卡 */ } }
```

或 widget_type 以 `sys.` 开头。Host 检测后挂起本轮，不让 LLM 续写"已完成"。

`awaiting_selection` 是 `awaiting_confirmation` 的多选版本——**当 tool 端有多个候选需要让用户挑一个时使用**。响应除 `status` 外应该带一张 `sys.selection` 卡片：

```json
{
  "ok": true,
  "status": "awaiting_selection",
  "canvas": {
    "action":      "inline",
    "widget_type": "sys.selection",
    "data": {
      "title":   "请选择目标菜谱",
      "options": [
        { "id": "recipe-001", "label": "番茄炒蛋",   "subtitle": "家常菜 · 10min" },
        { "id": "recipe-002", "label": "番茄牛腩面", "subtitle": "汤面 · 45min"  }
      ],
      "echo_args": { "request_text": "...", "...": "..." }
    }
  }
}
```

用户选完后，host 会用 `echo_args` + 用户选定的 `id` **再次调用同一个 tool**（自动追加为 `selected_id` 或对应业务字段）。AIPP 端要保证"用 echo_args + 选定 id 重入"会走通分支并产出最终结果。

### 8.5 状态枚举（推荐惯例）

Tool 响应建议在根上携带 `status`，让上层（LLM 与 SKILL.md playbook）按枚举分支处理而不是猜文本。**无需穷举**，但常用值：

| status | 含义 | 上层处理 |
|---|---|---|
| `ok` | 成功 | 继续后续步骤 |
| `not_found` | 资源不存在 | 转告用户，不静默创建 |
| `awaiting_confirmation` | 等用户确认（含 sys.confirm 卡片） | 挂起本轮 |
| `awaiting_selection` | 等用户多选一（含 sys.selection 卡片） | 挂起本轮 |
| `invalid_request` | 入参错误 | 报错，让用户/LLM 修正 |
| `unauthorized` | 无权限 | 转告 |
| `failed` / `request_failed` | 业务/系统失败 | 转告 `error` 字段，**不要静默重试** |

用 SKILL.md 时，playbook 应该按 `status` 列出每种分支的处理（参考 `employee_onboarding/SKILL.md`）。

### 8.6 `next_tool_recommended`（可选，跨 tool 软提示）

Tool 响应可在根上附：

```json
"next_tool_recommended": {
  "tool": "create_decision",
  "args": { "world_id": "...", "template_id": "onboarding_started" }
}
```

> ⚠️ 这是**软提示**，不是协议强制：LLM 是否遵循由对应 SKILL.md playbook 决定。**Tool 自身不能假设这一定会被调用**——如果你的业务必须串起两步，请把它们包进同一个 tool（server 端编排）或写成 Skill（LLM 端编排）。
>
> 反模式：在 tool description 里写"调完我之后请继续调 X" → 强迫 LLM 编排但又没 SKILL.md 兜底，行为不可预测。详见 §4.8。

### 8.7 `aap_hit` — tool 响应中动态激活 AAP-Post

Tool 响应可在根上附：

```json
"aap_hit": {
  "app_id":             "recipe-one",
  "post_system_prompt": "刚刚命中 recipe 域，回复格式...（见下方约定）",
  "ttl":                "this_turn | until_widget_close"
}
```

Host 看到 `aap_hit` 后，把 `post_system_prompt` 装入当前 GenericAgentLoop 的 AAP-Post 槽位（替换该 app 之前的 AAP-Post）：

| `ttl` | 生效范围 |
|---|---|
| `this_turn`（默认） | 仅本轮对话回复使用 |
| `until_widget_close` | 直到当前 canvas widget 关闭为止持续生效 |

用途：让"命中后的回复格式 / 结果解读规则"由 tool 自己决定，而不是写死在 `prompt_contributions` 里——便于按 env / 命中情况动态调整。

### 8.8 HTTP 状态码与错误体约定

| 场景 | HTTP 状态 | body |
|---|---|---|
| 成功（业务正常） | `200` | `{ ok: true, status: "ok", ...业务字段 }` |
| 业务"软失败"（如 `not_found`/`awaiting_*`） | `200` | `{ ok: true, status: "...", ... }` 或 `{ not_found: true, ... }` |
| 入参错误 | `400` | `{ ok: false, status: "invalid_request", error: "..." }` |
| 鉴权失败 | `401` / `403` | `{ ok: false, status: "unauthorized", error: "..." }` |
| 资源不存在（路径级，非业务） | `404` | `{ ok: false, status: "not_found", error: "..." }` |
| 服务端故障 | `500` | `{ ok: false, status: "failed", error: "..." }` |

**关键原则**：
- "用户/LLM 可理解的业务结果"（包括失败）用 **200 + `status` 字段**，让 LLM 按 status 分支
- "网络层/协议层错误"才用非 200，host 不会把这种响应交给 LLM 解析

---

## 9. Session 类型规范

| type | 说明 | 在 Task Panel 显示？ | session_id 格式 |
|------|------|---------------------|-----------------|
| `conversation` | 主对话（Host 自动创建） | ✅ 常驻 | `"main"` |
| `task` | 用户/LLM 发起的任务 | ✅ | UUID |
| `event` | 外部系统推送 | ✅ | UUID |
| `app` | AIPP 应用专属 | ❌ 不显示 | `"app-{appId}"` 或 `(appId, sessionId)` |

### App Session 路由

Skill 声明：

```json
"session": {
  "session_type": "app",
  "app_id":       "recipe-one",
  "creates_on":   "name",     // 可选：按参数创建多实例
  "loads_on":     "session_id"
}
```

响应同样可携带 `session_type` / `app_id` / `session_id`。Host 路由规则：

- `session_type=app` 且**无** `session_id` → 单实例：路由键 `appId`
- `session_type=app` 且**有** `session_id` → 多实例：路由键 `(appId, sessionId)`

### Session 归一原则

在某个已激活的 new-session widget 中再触发另一个 new-session widget时：**不创建**新 session，归一到当前 session，仅做 `canvas.open/replace`。

---

## 10. Host ↔ AIPP 运行时契约

> 本节是协议的"运行时入口"——AIPP 端写完前 9 节定义后，要让用户在 host 里真的能聊起来 / 看到 widget / 点交互，必须遵守本节列出的 6 套契约。Host 必须实现，AIPP 必须按这套消费/产出。

### 10.1 主对话端点 `POST /api/chat`（Host 实现，AIPP 知晓）

请求：

```json
{
  "session_id":  "main | task-... | app-recipe-one | <UUID>",
  "message":     "用户原文",
  "widget_view": { "widget_type": "recipe-board", "view_id": "FAVORITE" }
}
```

响应：`text/event-stream`，每条一行 `data: <ChatEvent JSON>\n\n`。

#### ChatEvent 类型清单（AIPP 必须知道的，按到达顺序可能交错出现）

| `type` | 何时发出 | content 形态 | AIPP 触发方式 |
|---|---|---|---|
| `text_token` | LLM 流式吐 token | 字符串片段 | LLM 自由生成 |
| `thinking` | LLM 推理流 | 字符串片段 | LLM 自由生成 |
| `tool_call` | 即将调一个 tool（含 universal） | `{ tool, args }` JSON | LLM 决定调用 |
| `annotation` | Router/Skill/系统注解（如"Router: load skill X"） | 字符串 | host 元数据 |
| `html_widget` | tool 响应根含 `html_widget` | `{ widget_type, title, data }` JSON | **AIPP 在 tool 响应根写 `html_widget`** |
| `canvas` | tool 响应触发 canvas 模式 | `{ action, widget_type, session_id, data }` JSON | **AIPP 写 `canvas` 或满足 `output_widget_rules.force_canvas_when`** |
| `session` | 新建/切换 task/app session | `{ name, type, app_id, canvas_session_id, widget_type, welcome_message }` | **AIPP 在 tool 响应写 `new_session` 或返回带 widget_type 的非新建** |
| `done` | 本轮结束 | `{}` | host 自动 |
| `error` | 异常 | `{ message }` | host 自动 |

→ AIPP 端要做的就是**写对 tool 响应**：写 `html_widget` 就出现卡片，写 `canvas` 或满足 force_canvas 就出现全屏 widget，写 `new_session` 就切换会话。**永远不要直接生成 ChatEvent**——那是 host 的事。

### 10.2 直调 tool 端点 `POST /api/apps/{appId}/open`（Host 实现）

绕过 LLM 直接调一个 tool，常用于：
- 用户点 Apps 面板里的应用图标 → host 调 AIPP 主入口 widget tool
- html_widget 卡片里的按钮通过 `hostApi.callTool(...)` 触发（详见 §10.3）

请求：

```json
{ "tool_name": "recipe_list_view", "tool_args": { /* ... */ }, "session_id": "<可选>" }
```

响应同 `/api/chat`：SSE 流，事件类型与 §10.1 相同。

→ AIPP 端**无需特殊适配**：host 内部其实就是构造一次 tool call → 调 AIPP 的 `POST /api/tools/{name}` → 把 tool 响应转成 SSE 事件。AIPP 只要让 tool 实现稳定即可。

### 10.3 html_widget ES module ↔ Host 的 `hostApi` 协议

`html_widget` 渲染为 app-owned ES module。Host 根据 `html_widget.widget_type`
找到 `/api/widgets` 中的 manifest，动态 import `render.url`，再调用：

```js
mod.mount(targetEl, hostApi, htmlWidget.data)
```

widget 内按钮等用户动作应调用 `hostApi.callTool(tool, args)` 发起新一轮 tool 调用；
内部刷新或读取 app 数据应调用 `hostApi.proxyTool(tool, args)` 或 `hostApi.appProxyUrl(path)`。
widget 不应使用 `parent.postMessage` 作为主协议，也不应读取 Host 的业务全局变量。

### 10.4 Canvas widget 与 host 的数据传递

Canvas widget（`widget_type` 在 `/api/widgets` 中以 `render.kind=esm + url` 注册）
通过同一套 ES module mount 协议挂载。Host 把 canvas payload 原样合并进 `data`，
并可在已挂载 widget 上调用通用的 widget-owned 入口（如 `aippHandleCanvasCommand(cmd)`）。

→ AIPP widget 端建议结构：

```js
export function mount(targetEl, hostApi, data) {
  renderWith(data);
}

function onOpen(id) {
  hostApi.callTool('recipe_open', { id });
}
```

多 Tab widget 的当前 view 通过工具返回或 widget data 同步；不要依赖 Host 内置 app UI。

### 10.5 AAP-Post 动态激活（与 §8.7 配套的运行时部分）

§8.7 描述了 tool 响应里 `aap_hit` 字段的形态，本节描述 host 处理它的运行时语义：

| host 步骤 | 行为 |
|---|---|
| 收到 tool 响应 | 解析根 `aap_hit` |
| `aap_hit.app_id` 为空 / 缺失 | 忽略（要求显式 app_id 才激活，避免错挂） |
| `aap_hit.ttl="this_turn"` | `post_system_prompt` 仅注入本轮 LLM 续写时的 system prompt |
| `aap_hit.ttl="until_widget_close"` | 持续注入直到当前 canvas widget 关闭 |
| 同 `app_id` 已有激活的 AAP-Post | 替换（不叠加） |
| 不同 app 的 AAP-Post | 互不干扰，按 prompt_contributions 顺序聚合 |

→ AIPP 用法：当某个 tool 命中后希望"接下来几轮的 LLM 回复格式"按特定方式来，就在响应里附 `aap_hit`，不需要把规则全塞进 `prompt_contributions`（那个是常驻的）。

### 10.6 Widget upload 协议——`prompt`/`tools[]` 在此处合法（§4.8 的唯一例外）

Widget manifest 可声明上传文件入口：

```json
"upload": {
  "accept":      "application/json,.json",
  "label":       "导入菜谱",
  "tool":        "recipe_import",
  "prompt":      "解析上传的 JSON，按顺序调 recipe_create 导入；逐条汇报。",
  "tools":       ["recipe_create", "recipe_get"]
}
```

这里的 `upload.prompt` / `upload.tools` 是 **widget 上传场景的 mini-agent 编排**（独立于聊天主流程），**不属于 §3 / §4.8 禁止的 tool entry 字段**。host 在用户点上传按钮、文件被读出后，把 `prompt` 注入一次性的 LLM 调用并限制其工具集为 `tools[]`。

> 这是 §4.8 "Tool entry 禁止 prompt/tools[]" 的**唯一例外**，因为它属于 widget 端协议，不属于 tool 端协议。

### 10.7 全流程范例：从用户输入到 widget 渲染

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
2. 实现 `POST /api/tools/recipe_open` 返回带 `graph` + `session_id` 的 JSON，并在 `world_open` 这个 tool 上声明 `output_widget_rules.force_canvas_when=["graph","session_id"]` + `default_widget="recipe-board"`

其余都是 host 自动处理。**没有任何 AIPP 特定代码进入 host**。

---

## 11. inject_context 协议

Skill 可声明需要 Host 注入的上下文：

```json
"inject_context": {
  "request_context": true,
  "turn_messages":   true
}
```

| 字段 | 效果 |
|------|------|
| `request_context: true` | 注入 `_context`（userId, sessionId, workspaceId, agentId） |
| `turn_messages: true` | 注入完整本轮消息列表（如记忆固化场景） |

---

## 12. memory_hints 协议

Skill 可声明执行后 Memory Agent 应关注的信息：

```json
"memory_hints": "关注用户的菜谱偏好和饮食习惯，记录为 PROCEDURAL 类型。"
```

Host 聚合所有 app 的 `memory_hints`，注入 Memory Agent system prompt（如有）。

---

## 13. LLM Context 多层架构（Host 视角）

Host 每轮对话的 prompt 按 6 层叠加（顺序固定）：

```
Layer 0：Host base + AAP-Pre/AAP-Post + Widget manual / view prompt
         （Host 铁律 + 各 AIPP 的 prompt_contributions[layer=aap_pre])

Layer 1：Memory Context（用户长期画像，可选）

Layer 2：Session Entry Prompt（task/event/app session 专有）

Layer 3：Widget llm_hint + Workspace info（仅 canvas 激活时）

Layer 4：Skill Playbook（progressive disclosure 加载的 SKILL.md）

Layer 5：UI Hints（最高优先级，前置到 sysContent 最前）

────────────────────────────────────────────
Layer 6：Session History（最近 N 条对话消息）
```

只要遵守 §6 的 6 个解耦字段，你的领域词、调度规则、UI 标签、事件订阅都会被 Host 在正确的 Layer 自动拼装到 prompt 中。

---

## 14. 合规规则速查（必看）

| 项 | 必选 | 要求 |
|----|------|------|
| `GET /api/app` | ✅ | 7 个字段全 |
| `GET /api/tools` 顶层 | ✅ | `app`, `version`, `tools` |
| `GET /api/skills` 顶层 | ✅ | `app`, `version`, `skills`（数组，可空） |
| `GET /api/widgets` 顶层 | ✅ | `app`, `version`, `widgets` |
| Tool entry | ✅ | `name`(snake_case), `description`, `parameters`(type=object) |
| Tool entry | ✅ | `canvas`（含 `triggers` boolean） |
| Tool entry | ❌ 禁用 | **不得**含 `prompt` / `tools[]` / `resources`（编排在 Skill 里） |
| Tool 顶层 | ✅ | `visibility`, `scope` |
| Skill 索引条目 | ✅ | `name`, `description`(40-1024 字符且含 WHEN), `allowed_tools`(非空), `playbook_url` |
| Skill 索引条目 | 推荐 | `level`(`app`/`widget`) + `owner_app`/`owner_widget` |
| Skill `allowed_tools` 元素 | ✅ | 每项都必须能在某个已注册 app 的 `/api/tools` 中找到 |
| `/api/skills/{name}/playbook` | ✅ | 返回 `text/markdown`；frontmatter 含 `name`/`description`/`allowed-tools`（注意连字符） |
| Widget | ✅ | `type`(非 `sys.*`), `app_id`, `is_main`, `is_canvas_mode`, `source`, `description` |
| Widget 非 `sys.*` | ✅ | `render` |
| **每个 app** | ✅ | **恰好一个 `is_main:true` widget** |
| `html_widget` | ✅ | `widget_type`, `title`, `data` |
| 跨端点一致性 | ✅ | `app_id` 在 `/api/app`、`/api/tools.app`、`/api/widgets.app` 必须相同 |
| `lifecycle` | 可选 | `on_demand` / `post_turn` / `pre_turn` |
| `output_widget_rules` | 可选 | `force_canvas_when`(数组) + `default_widget`(字符串) |
| `runtime_event_callbacks` | 可选 | 数组：`[{events:[…], path:"…"}]`（标准形态，详见 §6） |
| `event_subscriptions` | 可选 | 字符串数组；订阅方必须实现 `POST /api/events` |
| `display_label_zh` | 推荐 | tool 在前端的中文显示名 |
| `prompt_contributions` | 推荐 | 提供领域路由提示，让 LLM 准确分流 |

---

## 15. 合规验证工具（Java/JUnit）

放在 `aipp-protocol` 模块，AIPP 应用的测试类直接复用。

```java
AippAppSpec spec = new AippAppSpec();

// 结构验证
spec.assertValidAppManifest(appNode);
spec.assertValidToolsApiStructure(toolsNode);
spec.assertValidWidgetsApiStructure(widgetsNode);

// 跨端点一致性
spec.assertAppIdConsistency(appNode, toolsNode);
spec.assertWidgetTypesRegistered(skillsNode, widgetsNode);
spec.assertExactlyOneMainWidget(widgetsNode, List.of("recipe-one"));

// 解耦协议字段（v2.0）
spec.assertValidLifecycle(skill);
spec.assertValidOutputWidgetRules(skill);
spec.assertValidRuntimeEventCallback(skill);
spec.assertValidEventSubscriptions(subs);

// 工具响应
spec.assertToolResponseMatchesSkillCanvas("recipe_get", skillCanvas, response);
spec.assertCanvasOpenWithNewSession("recipe_create", response, "recipe-board");
spec.assertChatModeResponse("recipe_list", response);
```

```java
AippWidgetSpec wspec = new AippWidgetSpec();
wspec.assertWidgetSupportsDisable(widget);
wspec.assertWidgetThemeCoversProperties(widget, "background", "font", "language");
wspec.assertWidgetHasFullAppIdentity(widget);
wspec.assertHtmlWidgetResponse("recipe_list", response);
```

---

## 16. 不要做的事（反模式）

- ❌ 让 Host 知道你的 tool 名字。所有特化行为通过 §6 字段自描述。
- ❌ 让 Host 渲染你的业务 UI。Host 只调度，UI 必须由你 `render` 提供。
- ❌ 在 host system prompt 里加你的领域词。用 `prompt_contributions` 注入。
- ❌ 用 `sys.*` 作为 widget type 前缀（Host 系统保留）。
- ❌ 把多步事务硬塞成一个 Tool —— 应当拆出来写 SKILL.md，由 Host SkillRouter 渐进发现。
- ❌ 在 Skill description 里堆关键词列表"帮助召回"——纯靠 description 语义，关键词列表反而稀释信号。
- ❌ Skill description 不写 WHEN clause（"Use when ..." / "用于 ..." / "当用户 ..."）—— 写了 LLM 也召不到。
- ❌ Skill 的 `allowed_tools` 引用了未注册的 tool —— Host 会自动把 skill 从 catalog 移除。
- ❌ 在工具响应里同时返回 `html_widget` 和 `canvas` 而不声明 `output_widget_rules` —— Host 会优先 `html_widget`，你可能拿不到 canvas。
- ❌ `is_main` 多于一个或一个都没有 → 测试会红。
- ❌ 静默创建资源（找不到时直接 create 而不询问）→ 必须返回 `not_found` 让 LLM 转述。

---

## 17. 完整最小 AIPP 模板（可直接抄）

把下面 4 个端点实现完，注册到 Host，就是一个合规 AIPP。

<details>
<summary>展开示例</summary>

```http
GET /api/app
→ { "app_id":"recipe-one", "app_name":"菜谱", "app_icon":"<svg>...</svg>",
    "app_description":"菜谱管理", "app_color":"#ff8a65",
    "is_active":true, "version":"1.0" }

GET /api/tools
→ {
    "app":"recipe-one", "version":"1.0",
    "prompt_contributions":[{
      "layer":"aap_pre", "priority":100,
      "content":"用户提到「菜/菜谱/食材」走 recipe_* 工具。"
    }],
    "tools":[{
      "name":"recipe_list",
      "description":"列出菜谱（可按食材/分类筛选）。返回 html_widget 卡片。",
      "parameters":{ "type":"object","properties":{"query":{"type":"string"}},"required":[] },
      "canvas":{"triggers":false},
      "visibility":["llm","ui"],
      "scope":{"level":"universal","owner_app":"recipe-one","visible_when":"always"},
      "display_label_zh":"菜谱列表"
    }]
  }

GET /api/skills
→ {
    "app":"recipe-one", "version":"1.0",
    "skills":[]   // 没有多步流程时合规，可省略本端点
  }

GET /api/widgets
→ {
    "app":"recipe-one", "version":"1.0",
    "widgets":[{
      "type":"recipe-board",
      "app_id":"recipe-one",
      "is_main":true,
      "is_canvas_mode":true,
      "source":"external",
      "render":{"kind":"esm","url":"/widgets/recipe-board/recipe-board.js"},
      "description":"菜谱看板",
      "supports":{"disable":true,"theme":["background","surface","text","textDim",
                  "border","accent","font","fontSize","radius","language"]}
    }]
  }

POST /api/tools/recipe_list
← { "args":{"query":"番茄"}, "_context":{...} }
→ { "ok":true, "html_widget":{ "widget_type":"recipe-list","title":"菜谱","data":{...} } }
```

</details>

---

> **写给 LLM 的最后一句话**：
> 你看完本文档应当能：(1) 实现 4 个 HTTP 端点；(2) 写出合规的 Tool / Skill / Widget JSON；(3) 把多步事务写成 SKILL.md（progressive disclosure）；(4) 用 §6 的 6 个字段自描述所有"想让 Host 帮你特化处理"的行为，**绝不要求修改 Host 代码**。
> 不确定的字段去 `aipp-protocol` 的 `AippAppSpec` / `AippWidgetSpec` 找对应 `assert*` 方法 —— 那才是协议的最终事实。
