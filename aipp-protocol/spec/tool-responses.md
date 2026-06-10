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
| `aap_hit` | Dynamic AAP-Post for this turn / until widget close (README §8.7) |

---

## 6. HTTP status vs body

| Case | HTTP | Body |
|------|------|------|
| Business outcome (incl. `not_found`, `awaiting_*`) | **200** | `ok` + `status` or `not_found` |
| Protocol/validation failure | 400 / 401 / 404 / 500 | `{ "ok": false, "status": "...", "error": "..." }` |

LLM parses **200 + status** for branching; non-200 is Host-level failure.

---

## 7. What AIPP does not do

- Do **not** emit Host `ChatEvent` SSE directly — write correct tool JSON; Host translates (README §10.1).
- Do **not** return both `html_widget` and `canvas` without `output_widget_rules` unless you accept Host priority.

---

## Related

- Widget implementation: [`widgets.md`](widgets.md)
- Tool manifest: README §3 or [`host-decoupling.md`](host-decoupling.md)
