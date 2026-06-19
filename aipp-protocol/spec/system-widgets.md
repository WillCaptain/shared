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
| `sys.capability-browser` | Capability Catalog | Host skill | ❌ |
| `sys.capability-tree` | Capability Map | Host skill | ❌ |

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
