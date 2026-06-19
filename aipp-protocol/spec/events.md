# Host Events — Subscribe & Receive

> **Discovery:** [`AGENTS.md`](../AGENTS.md) → [`INDEX.md`](INDEX.md) → this file.  
> **Declare subscriptions:** [`host-decoupling.md`](host-decoupling.md) § `event_subscriptions`.

---

## 1. Declare subscriptions

On `GET /api/tools` response root (or per tool):

```json
"event_subscriptions": ["workspace.changed", "user.login", "session.closed"]
```

If you subscribe, you **must** implement `POST /api/events`.

Verify: `assertValidEventSubscriptions`.

---

## 2. `POST /api/events` (AIPP implements)

Host → your app (fire-and-forget):

```json
{
  "type": "workspace.changed",
  "payload": { "workspace_id": "ws-123", "user_id": "u1" },
  "timestamp": "2026-04-28T08:15:00Z"
}
```

Return HTTP 200 — Host does not wait for business logic completion.

---

## 3. Known generic event types

| type | Typical use |
|------|-------------|
| `workspace.changed` | Refresh widget context |
| `user.login` | Per-user cache reset |
| `session.closed` | Cleanup ephemeral state |

Apps may define additional types if Host dispatches them.

---

## 4. vs `runtime_event_callbacks`

| Mechanism | Direction | Use |
|-----------|-----------|-----|
| `event_subscriptions` | Host → AIPP push | Broad Host lifecycle events |
| `runtime_event_callbacks` | Host → AIPP POST to path | Decision results, action resume, domain-specific |

Both can coexist on the same app.

---

## Related

- [`host-decoupling.md`](host-decoupling.md) §3–§4 — declaring callbacks & subscriptions
- [`host-injection.md`](host-injection.md) — runtime bindings for listener apps
