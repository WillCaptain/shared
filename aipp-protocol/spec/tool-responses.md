# Tool Request & Response Protocol

> **Discovery:** [`AGENTS.md`](../AGENTS.md) → [`INDEX.md`](INDEX.md) → this file.  
> **Verify:** [`verify.md`](verify.md) — response `assert*` methods.  
> **`sys.*` shapes:** [`system-widgets.md`](system-widgets.md).

---

## 1. `POST /api/tools/{name}` request

```json
{
  "args": { "name": "番茄炒蛋" },
  "_context": {
    "userId": "user-1",
    "sessionId": "agent-session-id",
    "workspaceId": "canvas-session-or-null",
    "workspaceTitle": "optional",
    "agentId": "host-agent-id",
    "appBaseUrl": "http://your-aipp:port",
    "env": "production"
  }
}
```

| Rule | Detail |
|------|--------|
| Business params | `args` only (if `args` missing, Host may treat whole body as args) |
| `_context` | Read-only metadata from Host — **do not** echo back in business fields |
| `env` | From Host bindings — **never** switch env and retry yourself |

| `_context` field | Injected by | AIPP use case |
|---|---|---|
| `userId` | Host | Per-user isolation (scoped reads/writes) |
| `sessionId` | Host | Tie to a conversation; logging, idempotency |
| `workspaceId` | Host | Canvas mode only — "which object is being edited" (null outside canvas) |
| `workspaceTitle` | Host | Display only |
| `agentId` | Host | Which host agent is calling (multi-agent deploys) |
| `appBaseUrl` | Host | Your app's externally reachable address recorded at install; widgets read it via `hostApi.appBaseUrl` / `hostApi.appProxyUrl(path)` |
| `env` | Host | Current runtime env (same value as bindings — [`host-injection.md`](host-injection.md)) |

Persistent worker config: `PUT /api/host/bindings` — not `configuration.values`.

---

## 2. UI envelopes (pick one primary path)

### `html_widget` — chat inline

```json
{
  "ok": true,
  "html_widget": {
    "widget_type": "recipe-list",
    "title": "菜谱列表",
    "data": { "recipes": [] }
  }
}
```

For widgets with `display_mode: chat`.  
Same-type consecutive cards **replace**; otherwise **append**.

### `pop_widget` — floating layer

Same shape as `html_widget`. Widget manifest must have `display_mode: pop`.  
No chat message, no canvas stack.

### `canvas` — fullscreen / inline system card

```json
{
  "ok": true,
  "canvas": {
    "action": "open",
    "widget_type": "recipe-board",
    "session_id": "optional",
    "data": { }
  }
}
```

| `action` | Meaning |
|----------|---------|
| `open` | Open or focus canvas widget |
| `patch` | Update mounted widget data |
| `replace` | Replace canvas content |
| `close` | Close canvas |
| `inline` | Inline card (e.g. `sys.selection`) |

Host does **not** interpret app-specific fields inside `data` — only mounts ESM and passes payload through.

---

## 3. Host dispatch priority

1. If skill has `output_widget_rules.force_canvas_when` and **all** listed response fields exist and are non-empty → **canvas** (ignores `html_widget`).
2. Else if `pop_widget`, or `html_widget` with `display_mode=pop` widget → **pop**.
3. Else if `html_widget` → **chat card** (LLM stops continuing text).
4. Else if `canvas` → canvas handler.
5. Else normal text response.

Declare `output_widget_rules` on tool/skill when you need to override defaults — see [`host-decoupling.md`](host-decoupling.md).

---

## 4. Suspend turn (`status`)

```json
{
  "ok": true,
  "status": "awaiting_confirmation",
  "html_widget": { "widget_type": "sys.confirm", "title": "确认", "data": { } }
}
```

| status | Meaning |
|--------|---------|
| `ok` | Success |
| `not_found` | Resource missing — LLM relays, no silent create |
| `awaiting_confirmation` | Wait for user (`sys.confirm`, etc.) |
| `awaiting_selection` | Wait for pick (`sys.selection`) |
| `invalid_request` | Bad args |
| `unauthorized` | Permission denied |
| `failed` / `request_failed` | Business/system error |

Host must **not** let LLM write "done" summaries while `awaiting_*` is active.

### `not_found`

```json
{ "not_found": true, "message": "未找到「番茄炒蛋」。如需新建请确认。" }
```

User must confirm before create (e.g. `create_new: true` on recall).

### `sys.selection` from your tool

```json
{
  "ok": true,
  "status": "awaiting_selection",
  "canvas": {
    "action": "inline",
    "widget_type": "sys.selection",
    "data": {
      "title": "请选择",
      "options": [
        { "label": "A", "tool": "recipe_open", "args": { "id": "1" } },
        { "label": "取消", "message": "已取消" }
      ],
      "echo_args": { "request_text": "..." }
    }
  }
}
```

After user picks, Host re-invokes with `echo_args` + selection — your tool must handle that branch.

---

## 5. Optional response fields

| Field | Purpose |
|-------|---------|
| `new_session` | Open/switch session — see [`sessions.md`](sessions.md) |
| `session_type`, `app_id`, `session_policy`, `session_instance_key` | Session routing on response root |
| `next_tool_recommended` | Soft hint only — enforce flow via Skill or server-side orchestration |
| `entry_prompt_hit` | Dynamic entry prompt for this turn / until widget close (§5.1) |
| `session_summary` | Display title for decision-chain tasks — [`display-titles.md`](display-titles.md) |

### 5.1 `entry_prompt_hit` — dynamic entry prompt activation

```json
"entry_prompt_hit": {
  "app_id":  "recipe-one",
  "content": "刚刚命中 recipe 域，回复格式…",
  "ttl":     "this_turn | until_widget_close"
}
```

Use when a tool hit should shape the LLM's reply format for the next turn(s) without putting the rule permanently in `prompt_contributions` (which is resident).

Host runtime semantics:

| Case | Host behavior |
|------|---------------|
| `app_id` empty / missing | Ignored — explicit `app_id` required to avoid mis-attachment |
| `ttl: "this_turn"` (default) | `content` injected only into this turn's continuation system prompt |
| `ttl: "until_widget_close"` | Injected until the current canvas widget closes |
| Same `app_id` already has an active entry prompt | **Replaced** (not stacked) |
| Different apps | Independent; aggregated in `prompt_contributions` order |

---

## 6. HTTP status vs body

| Case | HTTP | Body |
|------|------|------|
| Business outcome (incl. `not_found`, `awaiting_*`) | **200** | `ok` + `status` or `not_found` |
| Protocol/validation failure | 400 / 401 / 404 / 500 | `{ "ok": false, "status": "...", "error": "..." }` |

LLM parses **200 + status** for branching; non-200 is Host-level failure.

---

## 7. What AIPP does not do

- Do **not** emit Host `ChatEvent` SSE directly — write correct tool JSON; Host translates ([`host-runtime.md`](host-runtime.md) §1).
- Do **not** return both `html_widget` and `canvas` without `output_widget_rules` unless you accept Host priority.

---

## Related

- Widget implementation: [`widgets.md`](widgets.md)
- Tool manifest: [`tool-manifest.md`](tool-manifest.md) + [`host-decoupling.md`](host-decoupling.md)
- Host SSE runtime: [`host-runtime.md`](host-runtime.md)
