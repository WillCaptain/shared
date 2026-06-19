# Decision reactor integration — catalog REST + session push

> Status: v2 (2026-06) · merges former `decision-reactor-invoke.md` + `ontology-world-catalog.md`  
> Related: [host-injection.md](./host-injection.md), [host-url.md](./host-url.md)  
> Verify: `DecisionReactorEntryTemplatesTest`, `DecisionReactorInvokeSpec`, `OntologyWorldCatalogSpec`

Two independent channels between an ontology world **provider** (e.g. world-entitir) and a **consumer** (e.g. decision-reactor):

| Channel | Transport | Host involved? |
|---------|-----------|----------------|
| **Catalog** (world list, entry templates) | Consumer → provider REST, direct | **No** — Host must not proxy catalog routes |
| **Session push** (`OntologySessionChangeEvent`) | Provider → consumer callback, direct | **No** — provider fans out to the consumer-registered `callback_url` |

## Roles

| Concern | Owner |
|---------|--------|
| `env` (runtime) | Host `PUT /api/host/bindings` only — never AIPP configuration |
| World list / entry templates | Consumer → provider REST; provider base URL in consumer configuration |
| `OntologySessionChangeEvent` delivery | Provider → consumer callback (direct push to a registered `callback_url`) |
| Session listener scope | Consumer registers `{world_id, entry_templates[]}` internally |

---

## 1. Catalog (REST convention, no Host proxy)

No Java SPI — **REST only**. Default path prefix on the provider process: `/api/decision-reactor-invoke`

| Method | Path | Description |
|--------|------|-------------|
| GET | `{prefix}/worlds?env=` | Worlds with a release for `env` |
| GET | `{prefix}/worlds/{worldId}/entry-templates?env=` | Entry-boundary decision templates |
| GET | `{prefix}/worlds/{worldId}/entity-outlines?env=` | (Optional) entity outline text for react preamble |

`env`: `production` \| `staging` (provider normalizes unknown values to `production`).

Entry-template filter: `org.twelve.aipp.invoke.DecisionReactorEntryTemplates` (manual / schedule / ontology activators; exclude `decision` chain steps).

### Consumer rules

- Store provider **base URL** in AIPP configuration (e.g. `ontology_world.base_url` → `http://127.0.0.1:8093`).
- Call provider REST **directly** for catalog reads.
- Take `env` from Host `PUT /api/host/bindings` when querying catalog (do **not** persist `env` in configuration).
- May expose a local BFF (e.g. `GET /api/catalog/worlds`) for browser widgets that run behind Host app-proxy.

### Response shapes

```json
// GET worlds
{
  "ok": true,
  "env": "production",
  "worlds": [
    { "world_id": "world-eai-onboarding", "world_name": "HR EAI", "release_version": 55 }
  ]
}
```

```json
// GET entry-templates
{
  "ok": true,
  "world_id": "world-eai-onboarding",
  "env": "production",
  "entry_templates": [
    {
      "template_id": "onboarding_started",
      "goal": "…",
      "context": "onboarding",
      "entry_type": "manual",
      "manual_enabled": true
    }
  ]
}
```

---

## 2. Session push (`ontology_session_change`)

Direct provider → consumer push. The Host is **not** on this path.

The consumer registers a `callback_url` per `{world_id, template_id}` on the provider:

```http
PUT {provider}/api/worlds/{worldId}/session-change-subscribers
{
  "subscriber_id": "decision-reactor:{worldId}:{templateId}",
  "template_id": "onboarding_started",
  "callback_url": "{consumer}/api/worlds/{worldId}/session-change"
}
```

`subscriber_id` **must** include both `world_id` and `template_id` so reactors for the
same template id across different worlds do not collide on the provider.

When a session flushes, the provider fans out directly to each matching subscriber:

```http
POST {consumer}/api/worlds/{worldId}/session-change
{
  "env": "production",
  "event": { ... }
}
```

Delivery is at-least-once and the consumer **must** dedupe on `event.id`. The
`callback_url` must be reachable by the provider process for the target deployment
(loopback for single-host; an externally reachable base URL otherwise).

Consumer dispatches react when:

1. `env` matches Host bindings
2. A registered session exists for `world_id`
3. `payload.root_template_id` ∈ registered `entry_templates`
4. Notification policy passes

### Notification policy

When the provider flushes `OntologySessionChangeEvent`, the payload includes:

| Field | Meaning |
|-------|---------|
| `entry_activation` | `manual` \| `schedule` \| `ontology` |
| `event_type` | Ontology entry: `external` or bus op (`CREATE` / `UPDATE` / `DELETE`) |

| `entry_activation` | Notify reactor? |
|--------------------|-----------------|
| `manual` | **Always** |
| `schedule` | **Always** |
| `ontology` | **Only when** `event_type == "external"` |
| (missing) | Notify (legacy) |

---

## 3. Independence rules

- Consumer **may** store ontology provider base URL in configuration for catalog only
- Consumer **must not** poll provider `/runtime-events`
- Provider **must not** import consumer types
- Session events are delivered **directly** provider → consumer `callback_url` (the Host is not on the push path)
- Host **must not** expose catalog proxy routes (e.g. `/api/decision-reactor-invoke/worlds` on world-one)
