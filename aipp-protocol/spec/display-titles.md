# Display Titles — Session / Event / Widget 三层标题命名

> **Discovery:** [`AGENTS.md`](../AGENTS.md) → [`INDEX.md`](INDEX.md) → this file.
> Session 路由与 `new_session`：[`sessions.md`](sessions.md)。Widget ESM：[`widgets.md`](widgets.md)。
> World 运行时侧摘要见 `ones/docs/world-runtime-contract.md` §7。

**问题**：决策链在 task panel、pending event 行、聊天区 widget 卡片上需要人类可读标题；且命名应与 `invoke_source`（LLM / manual / widget）解耦，只依赖链路上下文与 template metadata。**不得**在 Host 或 AIPP 中 hardcode 特定 template id 的展示名。

**与 `display_label_zh` 的区别**：`display_label_zh`（[`host-decoupling.md`](host-decoupling.md) §5）仅用于 **tool 在工具字典中的标签**。本节字段用于 **session、world event、widget 卡片**；新协议**不再**用 `display_name` 描述 widget/event/session 标题（旧字段 `tags.display_name` 仅作 Host 读兼容）。

---

## 1. 词汇表

| 面 | 字段 | 写入方 | 消费方 | 用途 |
|----|------|--------|--------|------|
| Session | `session_summary` | AIPP / entitir（`invoke_decision` 响应） | Host → `session.name` | 整条 task 的会话标题 |
| Chain entry | `step_summary` | entitir（chain 每条 activity） | entitir（推导下一步 label） | 单步执行摘要；不直接展示 |
| World event | `tags.event_label` | entitir（`parameter_missing` 等） | Host task panel pending 行 | pending event 展示名 |
| Widget | `context_title` | entitir / AIPP（写入 widget payload） | Widget ESM | 上游传入的默认标题 |
| Widget | `title` | Widget 作者（可选） | Widget ESM | Widget 自设标题（original name） |

`tags.decision` / `source.id` 保留为 **machine id**（如 `pc_pickup`），不作为默认展示名。

---

## 2. `session_summary` 解析（生产者，无 hardcode）

在 `invoke_decision`（或等价 tool）响应根写入 `session_summary`（≤48 字推荐）。解析优先级：

1. `args.session_summary` — 调用方 / LLM 显式传入
2. `args.preferred_session_title`
3. 从 `parameters` 与 template metadata 的**确定性**合成（读 schema / entity 字段，不枚举 template id）
4. `template.intent.goal`
5. `template_id`

同步写入 `session_display_name` / `new_session.name` 时，应与 `session_summary` 一致。

---

## 3. `step_summary`（chain entry）

每条 chain activity 映射时写入：

```json
{
  "template_id": "onboarding_started",
  "type": "EXECUTED",
  "step_summary": "孙艺菲入职已登记",
  "verdict": "…"
}
```

解析优先级：`verdict`（非空）→ `template.intent.goal` → `template_id`。

---

## 4. NEED_INPUT → `parameter_missing` event

创建下一 pending event 时，**上一步 summary** 作为本 event 的展示输入（与 LLM / manual 无关）：

```
event_label =
  lastChainEntry(types=[EXECUTED, RESUMED]).step_summary   // 有 prior chain 时
  ?? session_summary                                        // 链首步 NEED_INPUT
```

示例：

```json
{
  "type": "parameter_missing",
  "tags": {
    "decision": "pc_pickup",
    "event_label": "孙艺菲入职已登记"
  },
  "widget": {
    "widget_type": "auto_generated_form",
    "context_title": "孙艺菲入职已登记",
    "title": "补充参数后继续执行",
    "schema": { "fields": [ … ] }
  }
}
```

- `event_label` 与 `context_title` 通常相同（同一上游 summary，分别供 task panel 与 widget 使用）。
- `widget.title` 为 widget 作者可选的 **original name**；未设时 widget 使用 `context_title`。

---

## 5. Widget 标题解析（消费者，ESM 必须遵守）

Widget mount 时解析**聊天区卡片标题**：

```js
effectiveTitle =
  widget.title            // 1. widget 作者显式覆盖
  ?? widget.context_title // 2. 上游 summary（默认）
  ?? widget.schema?.title // 3. schema 默认
  ?? fallbackI18nKey      // 4. 内置兜底文案
```

Host **不**替 widget 决定 effective title；只透传 payload。

---

## 6. Host task panel — event 行

```js
eventLabel =
  tags.event_label
  ?? tags.display_name   // 仅兼容旧数据
  ?? tags.decision
  ?? type
```

---

## 7. 约束

- 命名管道只依赖 chain、`session_summary`、template metadata；**不得**按 `invoke_source` 分支。
- 禁止在 Host / AIPP 中为特定 world 或 template id 维护展示名映射表。
- `html_widget.title`（ChatEvent — [`host-runtime.md`](host-runtime.md)）与 `widget.title`（本节）语义相同：均为 widget 自设标题；`context_title` 为上游传入的默认来源。

---

## Related

- [`sessions.md`](sessions.md) §6 — `session_summary` 在 tool 响应上的位置
- [`widgets.md`](widgets.md) §8 — ESM 侧入口
- [`decision-reactor-integration.md`](decision-reactor-integration.md) — 决策链上下文
