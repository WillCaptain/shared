# Tool Manifest（`GET /api/tools`）

> **Discovery:** [`AGENTS.md`](../AGENTS.md) → [`INDEX.md`](INDEX.md) → this file.
> **Verify:** `assertValidToolsApiStructure`、`assertValidSkillStructure`（单个 tool entry）— [`verify.md`](verify.md)。
> Tool placement（`visibility` / `owner_widget` / …）：[`field-semantics.md`](field-semantics.md) + [`host-decoupling.md`](host-decoupling.md) §7。
> Tool 的 HTTP 执行与响应：[`tool-responses.md`](tool-responses.md)。

---

## 1. Tool 是什么

Tool 是 LLM / Widget UI / Host 直接调用的**原子函数**。从 LLM 视角看：**一次 call → 一次 response，全部完事**。Tool 内部允许在服务端 Java/Python 把多个内部 API 串起来，但**对 LLM 是黑盒**——LLM 不参与编排。需要 LLM 参与多步编排的能力，请用 Skill（[`skills.md`](skills.md)）。

> 直觉口诀：**LLM 看见就是契约，看不见就是实现**。LLM 看不见的多步串联是 Tool 的实现细节；LLM 看得见的多步流程是 Skill。

每个 tool entry 由 2 块组成：

```
┌─────────────────────────────────────────────────────────┐
│  AIPP 扩展层（可选）                                       │
│   canvas / session / output_widget_rules /               │
│   runtime_event_callbacks / lifecycle / event_subs /     │
│   display_label_zh / placement 字段                       │
├─────────────────────────────────────────────────────────┤
│  OpenAI function-calling 兼容层（必选）                    │
│   name / description / parameters                        │
└─────────────────────────────────────────────────────────┘
```

> ⚠️ **历史遗留警告**：早期版本曾有"Layer 2 Mini-agent"层（要求每个 tool 带 `prompt` / `tools[]` / `resources`），现已**彻底废弃**。`AippAppSpec.assertValidSkillStructure` 启动时会拒绝任何带这三个字段的 tool entry。Tool / Skill 判定准则见 [`skills.md`](skills.md) §1。

---

## 2. 兼容层（必选）

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

| 规则 | 说明 |
|------|------|
| `name` | 必须 snake_case |
| `parameters.type` | 必须为 `"object"`，且必须包含 `properties` 与 `required` |
| `description` | 描述**做什么 + 返回什么**；不要写"调完我之后请继续调 X"——那是 Skill 的事 |

---

## 3. AIPP 扩展层（可选）

```json
{
  "canvas":  { "triggers": true, "widget_type": "recipe-board" },
  "session": { "creates_on": "name", "loads_on": "session_id" },
  "output_widget_rules": {
    "force_canvas_when": ["graph", "session_id"],
    "default_widget":    "recipe-board"
  },
  "lifecycle":            "post_turn",
  "event_subscriptions":  ["workspace.changed"],
  "display_label_zh":     "查询菜谱",
  "visibility":           ["llm", "ui"],
  "router_promoted":      true
}
```

| 字段 | 详解 |
|------|------|
| `canvas` | `{ triggers: boolean, widget_type? }` — 是否打开 canvas widget；优先 `canvas.triggers` + widget `entry_tool` 映射（v2.5+），`assertValidSkillCanvasDeclaration` |
| `session` / `session_policy` | [`sessions.md`](sessions.md) |
| `output_widget_rules` / `lifecycle` / `runtime_event_callbacks` / `event_subscriptions` / `display_label_zh` / `prompt_contributions` | [`host-decoupling.md`](host-decoupling.md) §1–§6 |
| `visibility` / `owner_widget` / `router_promoted` / `router_promoted` / `mutates_display` / `catalog_manual` | [`host-decoupling.md`](host-decoupling.md) §7 + [`field-semantics.md`](field-semantics.md)（易错，先读） |
| `side_effect` | retry-safety 轴：`none` \| `idempotent` \| `mutating` — 见 §3.1，`assertValidSideEffectField` |
| `effect_identity` | `true` 时 Host 在 client dispatch 前向 owning AIPP 询问不透明 identity — [`effect-identity.md`](effect-identity.md) |
| `requires_authority` | 需要用户管理授权的能力必须登记为 function point — [`function-authority.md`](function-authority.md) |
| `requires_model_capabilities` | 模型能力门控；当前只支持 `["vision"]`。Host 按元数据过滤，不得按 tool 名硬编码 — [`client-execution.md`](client-execution.md) §2 |
| `inject_context` / `memory_hints` | [`skills.md`](skills.md) §6 |

---

## 3.1 `side_effect` — retry-safety 轴（编排器用）

**On:** `GET /api/tools` 的每个 tool entry（可选）。

回答的唯一问题：**当 Host 在多步协作计划（cooperative plan）中执行到这一步并失败后，能否安全地自动重试？** 这是独立于 placement / `mutates_display` / `requires_confirmation` 的第四正交轴 —— 不要混用（详见 [`field-semantics.md`](field-semantics.md) §1）。

| 值 | 含义 | 编排器重试策略 |
|----|------|----------------|
| `none` | 只读，无外部副作用 | 任何失败都可重试 |
| `idempotent` | 有写，但重复执行 == 单次执行（按业务键去重 / upsert） | 任何失败都可重试 |
| `mutating` | 非幂等写（发邮件、扣款、追加记录） | **仅** pre-send 失败（连接拒绝 / DNS，请求确未送达）可重试；post-send 失败（timeout / 5xx，服务端状态未知）**禁止**自动重试，编排器停下交还用户 |

**默认（未声明）**：Host 必须按最不安全 = `mutating` 处理（fail-closed）。漏标的工具绝不会被自动重试 post-send 失败。该默认是 Host 行为，校验器只校验枚举合法性（与 `execution_surface` 默认 `server` 同理）。

```json
{ "name": "send_invoice_email", "side_effect": "mutating", "requires_confirmation": true }
{ "name": "crm_profile_get",    "side_effect": "none" }
{ "name": "tag_upsert",         "side_effect": "idempotent" }
```

> **作者规则**：写工具默认就该显式声明。`idempotent` 是你给编排器的承诺 —— 只有当后端真的按业务键去重时才标，否则标 `mutating`。未来 Host 可下发幂等键（idempotency key）让 `mutating` 也可重试，但那是 v2，现在别依赖。

---

## 3.2 Canvas resource binding

Canvas resource binding: a tool may declare `canvas_resource_parameters: ["document_id"]`.
Each listed argument denotes the current canvas resource (not arbitrary references or child entity IDs).
For the owning app's canvas, the Host binds absent/blank values to its authoritative workspace ID
and rejects conflicting values before dispatch. Providers use the same `CanvasResourceBinding`
rule before accessing data. Outside canvas mode the declaration does not restrict explicit targets.
The declaration is Host metadata and is removed from model-visible function schemas.

`assertValidCanvasResourceParameters` validates these keys against string properties in the tool schema.
A Canvas with missing identity must fail closed for resource-bound calls. This scope supplements
user/function authorization; `_context` is trusted Host metadata, not a public API authentication mechanism.

## 4. `GET /api/tools` 根级结构

```json
{
  "app":     "recipe-one",
  "version": "1.0",
  "prompt_contributions":     [ /* 根级领域提示 — host-decoupling.md §6；勿用已弃用的根级 system_prompt */ ],
  "event_subscriptions":      [ /* host-decoupling.md §4 */ ],
  "runtime_event_callbacks":  [ /* host-decoupling.md §3 */ ],
  "tools": [ /* tool 对象 */ ]
}
```

顶层必选：`app`、`version`、`tools`。`app` 必须与 `/api/app.app_id` 一致（`assertAppIdConsistency`）。

---

## Related

- [`skills.md`](skills.md) — Tool vs Skill 边界、`/api/skills`
- [`host-decoupling.md`](host-decoupling.md) — 解耦字段全集
- [`field-semantics.md`](field-semantics.md) — placement / 副作用 / 刷新三轴
- [`tool-responses.md`](tool-responses.md) — `POST /api/tools/{name}` 请求与响应

## Agent execution hints

Tools may opt into `inject_context.user_message`, `active_skill`, and
`selected_resources` (booleans). The Host sets `_context.invocationKind=agent`
and sends `userMessage`, `activeSkill`, or `selectedResources` only when opted in.
Active skill identity is restricted to its owner and allowed tools. Selected resource
metadata is bounded UI data, never evidence or authorization. Providers interpret
their own resource names and filters within the authenticated user's scope.

Host-lifecycle tools declare `pre_turn_context_pointer: "/path/to/text"` to select
their reference text from a successful JSON response. Missing declarations, errors,
non-text fields and failed HTTP responses contribute nothing. Each contribution is
bounded to 16,000 characters. Hosts combine contributions rather than overwrite
another provider's context; cached values are isolated by provider, tool and resource.
These fields are metadata and are stripped from model function schemas.

Optional `block_retries_after_timeout: true` blocks further calls to the same tool
in the current foreground turn after a sent HTTP request times out. It only adds a
restriction: false or omission does not authorize retries otherwise forbidden by
side-effect safety, permissions, or execution scope. The Host reports the timeout;
it must not silently substitute another provider operation. A provider may implement
its own bounded fallback inside its atomic tool, or expose alternatives for explicit
selection through ordinary capability discovery.

Optional `router_defer_arguments: true` prevents a router shortcut from inventing
arguments: normal executor resolution runs first. It does not grant tool access,
skip confirmation, or alter side-effect policy.

Optional `agent_result` is a data-only model-result projection:
`{evidence_paths: ["/records/0"], projection: {records: {path: "/records", limit: 3,
items: {title: "/title"}}}, synthesize: true}`.
Strings in the projection are JSON pointers relative to the current object;
nested objects construct output objects. Array descriptors use `path`, `items`
and optional `limit` (default 10, range 1–32).
All 1–16 evidence pointers must resolve to non-null, nonempty evidence.
Projection depth is limited to 8, fields to 64 per object, and serialized output
to 16,000 characters. Malformed configuration or unsupported JSON leaves the
original response unchanged. Errors, pending interactions, and UI receipts are
never projected. `synthesize` asks the foreground agent to answer from the
returned evidence; it is not a success/authorization signal. The policy describes
model context shaping, not a replacement for authoritative tool receipts.
Validation: `AippAppSpec.assertValidAgentPolicy`.


### Provider lifecycle state (optional)

`pre_turn_state_pointer` selects an opaque JSON object alongside `pre_turn_context_pointer`.
`pre_turn_cache_ttl_ms` is an integer 0–60000, default 0 (fresh response required each turn).
A provider opting into reuse guarantees validity through that lease, including revocation.
Host distinguishes successful empty output from failure and fences older requests.
`inject_context.execution_scope: true` supplies `_context.executionScope` with `contextKind`
(main/subtask/workspace), `taskKind` (main/subtask), `workspaceId`, `workspaceOwnerAppId`.
These are context selectors, not resource content or authority grants; ordinary workspace
argument binding retains app-owner restrictions.
`inject_context.provider_contexts: true` on a post-turn tool receives only the same app's
contributions attached to an attempted model request, via top-level `provider_contexts`.
Each entry has `version: 1`, `source_tool`, `scope`, `context_text`, optional provider `state`,
`age_ms`, `stale`, and `truncated`. Host preserves opaque state and exact supplied text;
providers must validate references against trusted identity/scope and handle truncation.
No receipt proves model consumption or supplies durable turn coverage by implication.
These fields are hidden from model tool schemas. Legacy text-only tools remain compatible.
