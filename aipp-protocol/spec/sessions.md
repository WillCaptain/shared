# Sessions & Canvas Open

> **Discovery:** [`AGENTS.md`](../AGENTS.md) → [`INDEX.md`](INDEX.md) → this file.  
> **Verify:** `assertValidSkillSessionExtension`, `assertCanvasOpenWithNewSession` — [`verify.md`](verify.md).  
> **Tool responses:** [`tool-responses.md`](tool-responses.md).

---

## 1. Session types

| type | Task panel | Typical `session_id` |
|------|------------|----------------------|
| `conversation` | ✅ main chat | `"main"` |
| `task` | ✅ | UUID |
| `event` | ✅ | UUID |
| `app` | ❌ hidden | `"app-{appId}"` or keyed instance |

AIPP does **not** create Host chat sessions directly — return `new_session` / session fields in **tool responses**; Host emits `session` ChatEvents.

---

## 2. Opening canvas with `new_session`

```json
{
  "ok": true,
  "canvas": {
    "action": "open",
    "widget_type": "recipe-board",
    "widget_id": "recipe-board",
    "data": { }
  },
  "new_session": {
    "name": "菜谱管理",
    "type": "app",
    "welcome_message": "面板已打开。"
  }
}
```

Verify: `assertCanvasOpenWithNewSession(toolName, response, expectedWidgetType)`.

---

## 3. `session_policy` (required for app sessions)

Avoid duplicate Task Panel rows — declare on response root and/or `new_session`:

| `session_policy` | Meaning |
|----------------|---------|
| `singleton` | One row per `app_id` (e.g. decision-reactor, memory-one) |
| `keyed` | One row per `(app_id, session_instance_key)` (e.g. per `world_id`) |

```json
{
  "session_type": "app",
  "app_id": "world",
  "session_policy": "keyed",
  "session_instance_key": "world-eai-onboarding",
  "new_session": {
    "name": "EAI Onboarding",
    "type": "app",
    "session_policy": "keyed",
    "session_instance_key": "world-eai-onboarding"
  }
}
```

| Rule | Detail |
|------|--------|
| Business keys only | Provide `app_id` + `session_instance_key` — **not** Host internal `ui_session_id` |
| `keyed` | `session_instance_key` required, non-blank |
| Precedence | Response root policy **overrides** `new_session` when both present |
| Host rejects | Invalid policy or `keyed` without key |

Verify: `assertValidSkillSessionExtension(skillOrToolEntry)`.

---

## 4. Skill `session` block (declarative)

On tool entry in `/api/tools`:

```json
"session": {
  "session_type": "app",
  "app_id": "recipe-one",
  "creates_on": "name",
  "loads_on": "session_id"
}
```

Host uses this with tool args to decide create vs load. For cross-app routing prefer `session_policy` + `session_instance_key` on responses.

---

## 5. Session normalization

When a canvas widget is already active, another `new_session` from the **same** flow should **not** spawn a second session — Host normalizes to current session and uses `canvas.open` / `replace`.

Design tools so re-entry with `loads_on` / same `session_instance_key` is idempotent.

---

## 6. `session_summary` (display titles)

For decision-chain / task titles, write on tool response (e.g. `invoke_decision`):

```json
"session_summary": "孙艺菲入职登记"
```

Resolution priority (producer):  
`args.session_summary` → `args.preferred_session_title` → parameter synthesis → `template.intent.goal` → `template_id`

Keep ≤48 chars recommended. Align `new_session.name` with `session_summary`.

Details: README §6.7.

---

## Related

- Host SSE event `session`: README §10.1
- Widget welcome: [`widgets.md`](widgets.md)
