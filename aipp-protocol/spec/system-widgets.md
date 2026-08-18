# Host System Widgets (`sys.*`) — 协议规格

**受众**：AIPP 应用开发者、AI 编排开发者、Host（world-one）实现者。

**发现路径**：[`AGENTS.md`](../AGENTS.md) → [`INDEX.md`](INDEX.md) → 本文。

**归属**：`sys.*` widget 由 **Host 内置实现**。AIPP 应用 **不得** 在 `GET /api/widgets` 中注册 `sys.*` 类型（`AippAppSpec` 合规检查会拒绝）。

**可执行规范**：

- Java 常量：`org.twelve.aipp.AippSystemWidget`
- 可执行示例测试：`AippSystemWidgetSpecTest`
- 运行时目录：`GET {host}/api/widgets`（合并了各 app widget + `worldone-system` 注册的 Host widget）

---

## 1. 开发者需要记住什么

| 问题 | 答案 |
|------|------|
| 要不要在自家 `/api/widgets` 注册 `sys.confirm`？ | **不要** |
| 能不能在 tool 响应里写 `"widget_type": "sys.confirm"`？ | **可以** |
| 返回的是 tree 节点 id 还是 widget type？ | **`widget_type` 字符串**（如 `sys.selection`），不是 `appId::widget-…` 节点 id |
| 去哪查有哪些 `sys.*`？ | 本文 + `AippSystemWidget` + Host `GET /api/widgets` |
| capability tree 里 `widgets/` 文件夹干什么？ | **目录/预览**，不是 Router 执行目标 |

**铁律**：Router 发现的是 **tool / skill / route** 叶节点；`sys.*` 是 **展示协议**，由 tool 响应或 Host 运行时选择。

---

## 2. 在 tool / skill 响应中引用系统 widget

### 2.1 通用 envelope

Host 识别以下三种携带 UI 的响应字段（见 [`tool-responses.md`](tool-responses.md) §2）：

```json
{
  "ok": true,
  "html_widget": {
    "widget_type": "recipe-list",
    "title": "菜谱列表",
    "data": { }
  }
}
```

```json
{
  "ok": true,
  "pop_widget": {
    "widget_type": "sys.configuration",
    "title": "应用配置",
    "data": { }
  }
}
```

```json
{
  "ok": true,
  "canvas": {
    "action": "open",
    "widget_type": "sys.confirm",
    "data": { }
  }
}
```

| 字段 | 适用 widget |
|------|-------------|
| `html_widget` | `display_mode: chat` |
| `pop_widget` | `display_mode: pop` |
| `canvas` | `display_mode: canvas`，或 inline 系统卡片（`action: open` / `inline`） |

`widget_type` 为 `sys.*` 时，Host 用内置渲染器；**不要求**该类型出现在 AIPP 自己的 manifest 里。

### 2.2 挂起本轮（等用户操作）

推荐在根上带 `status`：

| status | 典型 widget |
|--------|-------------|
| `awaiting_confirmation` | `sys.confirm` |
| `awaiting_selection` | `sys.selection` |

Host 见到后 **不应** 让 LLM 继续写「已完成」类总结。

### 2.3 Java 常量（推荐）

```java
import org.twelve.aipp.AippSystemWidget;

canvas.put("widget_type", AippSystemWidget.CONFIRM);
canvas.put("widget_type", AippSystemWidget.SELECTION);
```

---

## 3. 系统 widget 一览

| `widget_type` | 标题（catalog） | 谁通常发出 | AIPP 能否直接返回 |
|---------------|-----------------|------------|-------------------|
| `sys.confirm` | Confirm | AIPP tool | ✅ |
| `sys.alert` | Alert | AIPP tool | ✅ |
| `sys.prompt` | Prompt | AIPP tool | ✅ |
| `sys.selection` | Selection | AIPP tool **或** Host Router/Planner | ✅ |
| `sys.choice` | Choice | 同 `sys.selection`（兼容别名） | ✅（推荐用 `sys.selection`） |
| `sys.progress` | Progress | AIPP tool / Host 默认进度 | ✅ |
| `sys.configuration` | AIPP Configuration | **Host 组装** | ❌（返回 `pop_widget` 时 Host 填 `data`） |
| `sys.app-info` | AIPP Information | **Host 组装** | ❌ |
| `sys.app-list` | AIPP List | Host skill | ❌ |
| `sys.parameter-missing` | Parameter Missing | Host（`parameter_missing` 事件） | ❌ |
| `sys.approval` | Approval | Host（决策审批 / HITL） | ⚠️ 一般不由业务 AIPP 直接拼 |
| `sys.plan` | Collaborative Plan | **Host Free Planner** | ❌ |
| `sys.todo` | Work TODO | **Host adaptive loop** (`todo` meta tool) | ❌ |
| `sys.work` | Work | **Host WorkService** (`run_work`) | ❌ |
| `sys.capability-tree` | Capability Map | Host skill | ❌ |
| `sys.download` | Download Once | **Host** (no client executor) | ❌ |
| `sys.terminal` | Terminal | **Host** (`terminal_run` one-shot) | ❌ |

> `auto_generated_form` 为 `sys.parameter-missing` 的运行时别名。

---

## 4. 各类型 `data` 结构

### 4.1 `sys.confirm`

```json
{
  "mode": "yes_no",
  "title": "确认删除",
  "message": "确定删除这 3 条记录？此操作不可撤销。",
  "danger": true,
  "yes": {
    "tool": "my_delete_confirmed",
    "args": { "ids": ["a", "b"] }
  },
  "no": {
    "message": "已取消"
  }
}
```

| 字段 | 说明 |
|------|------|
| `mode` | `yes_no` \| `ok_cancel` |
| `danger` | `true` 时确认钮为危险样式 |
| `yes.tool` | 用户确认后 Host 代理调用的 tool |
| `no.message` | 取消后写入 chat 的文案（可选） |

### 4.2 `sys.alert`

```json
{
  "title": "操作完成",
  "message": "已成功保存。",
  "close_message": "用户已确认"
}
```

### 4.3 `sys.prompt`

```json
{
  "title": "输入名称",
  "message": "请输入菜谱名称：",
  "placeholder": "例如：番茄炒蛋",
  "submit": {
    "tool": "recipe_create",
    "arg_name": "name"
  },
  "cancel": {
    "message": "已取消"
  }
}
```

### 4.4 `sys.selection`

```json
{
  "title": "请选择目标",
  "message": "检测到多个匹配项：",
  "options": [
    {
      "label": "方案 A",
      "tool": "recipe_open",
      "args": { "id": "recipe-001" },
      "node_id": "recipe-one::leaf-a"
    },
    {
      "label": "方案 B",
      "tool": "recipe_open",
      "args": { "id": "recipe-002" }
    },
    {
      "label": "取消",
      "message": "已取消"
    }
  ],
  "free_plan_slot": "slot-1"
}
```

| 字段 | 说明 |
|------|------|
| `options[].tool` | 用户点选后执行的 tool（Host 代理） |
| `options[].message` | 无 tool 时写入 chat |
| `options[].node_id` | 可选；Free Planner 回填 capability 叶 id |
| `free_plan_slot` | Host 内部计划槽位 id（可选） |

**`awaiting_selection` 重入约定**（[`tool-responses.md`](tool-responses.md) §4）：可附带 `echo_args`；用户选完后 Host 用 `echo_args` + 选定 id **再次调用同一 tool**。

### 4.5 `sys.progress`

```json
{
  "title": "正在处理",
  "message": "请稍候…",
  "indeterminate": true,
  "poll_tool": "job_status",
  "poll_interval": 2000
}
```

### 4.6 `sys.configuration` / `sys.app-info`（Host 组装）

AIPP **不**实现这两个 renderer。业务 app 只需：

- 配置 UI：`GET /api/app` → `configuration.ui`（见 [`configuration.md`](configuration.md)）
- 主入口无 Canvas：manifest `main_widget_type: "sys.app-info"`

Host 打开配置/信息时组装 `pop_widget` / `html_widget` 的 `data`。

### 4.7 `sys.plan` / `sys.approval`（Host 编排）

由 Host Free Planner、决策反应器发出。AIPP 开发者只需保证 **tool/skill 叶** 在 capability tree 中可被正确发现；不必手动返回 `sys.plan`。

`sys.plan` 的 Free Plan DAG v2 payload：

```json
{
  "schema_version": "worldone.free_plan/v2",
  "plan_id": "8f2c...",
  "revision": 2,
  "status": "awaiting_approval",
  "message": "Onboard Sarah and assign a laptop",
  "nodes": [
    {
      "id": "create_person",
      "question": "Create Sarah's employee record",
      "depends_on": [],
      "status": "succeeded",
      "risk": "read_only",
      "output": { "person_id": "p-1" }
    },
    {
      "id": "assign_laptop",
      "question": "Assign an available laptop to Sarah",
      "depends_on": ["create_person"],
      "status": "awaiting_approval",
      "risk": "mutation"
    }
  ],
  "edges": [
    { "from": "create_person", "to": "assign_laptop", "type": "data" }
  ],
  "evidence": [],
  "requires_approval": true,
  "can_execute": true
}
```

`plan_id` 在 revisions 间稳定；`revision` 单调递增。Host 根据实际选中的 tool
`side_effect` 设置 `risk`，不能仅信任编译器预测。`requires_approval=false` 表示
Host 可自动执行只读图，不代表 AIPP 可绕过 Host 风险门。
`can_execute=false` 表示仍有 no-match / ambiguity / invalid binding，UI 只允许修改，
不得展示 Continue。

### 4.8 `sys.todo` / `sys.work`（Host 工作进度）

由 Host adaptive loop 与 durable work 路径发出。AIPP **不得**注册这些类型；
业务 tool 也 **不应**直接拼装它们（除非未来明确的 Host 代理 API）。

**稳定实例键（in-place 更新）**

| `widget_type` | 键字段 | 典型 `action` |
|---------------|--------|---------------|
| `sys.todo` | `data.todo_list_id` | `replace` |
| `sys.work` | `data.work_id` | `replace` |

重复 `replace` 必须携带单调递增的 `data.revision`。Widget JSON 不是状态权威：
TODO 状态在 loop；work 状态在 `TaskStore`。

#### `sys.todo`

```json
{
  "action": "replace",
  "widget_type": "sys.todo",
  "data": {
    "todo_list_id": "todo_turn_01J",
    "owner": { "kind": "session", "id": "session-1" },
    "status": "running",
    "revision": 2,
    "items": [
      { "id": "inspect", "title": "Inspect routing", "status": "done" },
      { "id": "test", "title": "Run focused tests", "status": "in_progress" }
    ]
  }
}
```

| 字段 | 说明 |
|------|------|
| `todo_list_id` | 稳定卡片 id（通常 per-turn） |
| `owner` | `{ kind: session \| dag_node, id }` |
| `status` | `running` \| `completed` |
| `items[].status` | `pending` \| `in_progress` \| `done` \| `blocked` \| `cancelled` |
| `revision` | ≥ 1；延迟事件不得回退 UI |

TODO 是**瞬时引导**，不是 durable task。UI 不得暗示可跨 Host 重启恢复。
`data.status` 表示列表生命周期，而不是成功/失败结果：只要任一 item 仍是
`pending` / `in_progress`，列表就是 `running`；全部 item 进入
`done` / `blocked` / `cancelled` 后，列表就是 `completed`。UI 必须信任
Host 提供的列表状态，不得在前端派生新的协议外状态；但 completed 列表中若有
blocked / cancelled item，可以用 warning tone 呈现，不能误画成全成功。

#### `sys.work`

```json
{
  "action": "replace",
  "widget_type": "sys.work",
  "data": {
    "work_id": "task_01J",
    "status": "needs_review",
    "runner_kind": "step_director",
    "title": "Prepare and publish report",
    "revision": 4,
    "task_ui_session_id": "ui-task-1",
    "actions": ["open_work_panel", "rerun", "skip", "abort", "cancel"],
    "items": [
      { "id": "step-1", "title": "outline_grammar — index", "status": "done" },
      { "id": "step-2", "title": "outline_parse", "status": "in_progress", "detail": "running" }
    ]
  }
}
```

`sys.work` 是统一、durable 的规范投影。`runner_kind` 仅供诊断，不能作为用户或模型选择；
`work_id` 在全部 revision 间稳定。
协议只暴露 `step_director` / `agent_child`；Host 内部的并行 child 调度模式（例如
`AGENT_CHILD_BATCH`）仍投影为 `agent_child`，不能扩张 widget runner 枚举。

| 字段 | 说明 |
|------|------|
| `items[]` | 可选。步骤 / 单元 / 指令目标的列表；UI 必须按列表渲染，不得压成一行 |
| `items[].status` | `pending` \| `in_progress` \| `done` \| `blocked` \| `cancelled` |
| `items[].detail` | 可选。该项的简短结果/原因（如 `no_match`） |
| `result_summary` | 可选。终态卡片上的短结果（≤ 240 字符）；完整结果由 Host 追加到 parent conversation |

`sys.work` 保持统一的紧凑卡片结构（title / status / message / details / actions）。
`items[]` 仅在存在真实工作项时插入该结构，不能把整张卡替换成另一种列表 widget：

- `STEP_DIRECTOR`：items 来自声明的 steps，并由真实 step cursor/result 驱动；
- batch work：items 来自真实 units；
- `AGENT_CHILD`：多步 child 必须先通过 `todo` 发布自己的 items，并在执行中更新；
- Host / widget 不得为缺失的 items 猜测或硬编码“分析、总结”等阶段。

Widget 只渲染 Host 投影，不从 tool-call 日志臆造工作项。终态时 Host 在 parent
conversation 追加完整 summary；卡片只保留 `result_summary` 短结果，避免重复长文。

可执行校验：`AippWorkProgressSpec.assertValidSysTodoCanvas`（见 `AippWorkProgressSpecTest`）。
规范 Work 使用 `AippWorkProgressSpec.assertValidSysWorkCanvas`。

### 4.N `sys.download`

Host-owned Once installer card. Emitted when the user asks for a **client-only**
capability and the session has no desktop executor. AIPP apps must not register
this type.

```json
{
  "title": "Download Once",
  "message": "This needs the desktop client.",
  "page_url": "https://12th.ai/ones/download/",
  "downloads": [
    { "id": "mac_arm64", "label": "macOS (Apple Silicon)", "url": "/ones/download/artifacts/Once-latest-mac-arm64.dmg", "kind": "dmg" },
    { "id": "mac_x64", "label": "macOS (Intel)", "url": "/ones/download/artifacts/Once-latest-mac-x64.dmg", "kind": "dmg" },
    { "id": "win_x64", "label": "Windows", "url": "/ones/download/artifacts/Once-latest-win-x64.exe", "kind": "exe" }
  ]
}
```

| 字段 | 说明 |
|------|------|
| `downloads[]` | Required. At least macOS and Windows installers. |
| `downloads[].kind` | `dmg` or `exe` |
| `page_url` | Optional landing/chooser URL |

Host must offer both macOS and Windows. Do not auto-start a single binary.
The widget may highlight the installer that matches this browser's OS/arch.

### 4.N+1 `sys.terminal`

Host-owned local shell card. A **chat-typed** command opens a new session
(new card). Typing in the card continues that session's cwd. Fixed-height
scrollback; not an interactive PTY. Editors and other TTY programs
(`vi`/`vim`/`less`/`top`/`ssh`, bare REPLs) open the user's real local
terminal instead. AIPP apps must not register this type.

```json
{
  "session_id": "term_ab12cd34ef56",
  "cwd": "/Users/imac/Documents",
  "home": "/Users/imac",
  "revision": 2,
  "lines": [
    { "command": "cd Documents", "stdout": "", "stderr": "", "exit_code": 0, "ok": true },
    { "command": "pwd", "stdout": "/Users/imac/Documents", "stderr": "", "exit_code": 0, "ok": true }
  ]
}
```

| 字段 | 说明 |
|------|------|
| `session_id` | Host session; widget input continues this id |
| `cwd` | Working directory for the next command |
| `home` | Session home (`cd` / `Set-Location` with no target) |
| `revision` | Scrollback length; Host upserts history by `session_id` |
| `lines[]` | Scrollback; Host keeps a bounded tail |

---

## 5. 与自有 widget 的关系

```
你的 AIPP
  GET /api/widgets  →  仅注册自有 type（如 recipe-board）
  POST /api/tools/x →  可返回：
                         · html_widget.widget_type = recipe-board
                         · canvas.widget_type = sys.confirm
```

| 类型 | 注册在 `/api/widgets` | 出现在 capability tree `widgets/` |
|------|-------------------------|-----------------------------------|
| 自有 widget | ✅ 必须（`is_main` 之一） | ✅ 自动同步 |
| `sys.*` | ❌ 禁止 | ✅ 仅在 `worldone-system` 下作 Host 目录 |

---

## 6. 自查清单

- [ ] 未使用 `sys.` 作为自有 widget `type`
- [ ] 危险/不可逆操作使用 `sys.confirm` + `yes.tool` 二次调用
- [ ] 多候选消歧使用 `sys.selection` 或 `status: awaiting_selection`
- [ ] 自有 UI 使用 manifest 中的 `type`，且 `render.url` 可加载
- [ ] 需要 Host 能力列表时读 `AippSystemWidget`，而非硬编码遗漏新类型

---

## 7. 相关文档

- [`../README.md`](../README.md) §5 Widget、§8 响应约定
- [`capability-tree.md`](capability-tree.md) — 可执行能力树（与 `sys.*` 目录分离）
- [`configuration.md`](configuration.md) — `sys.configuration` 的数据来源
