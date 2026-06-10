# Ontology world catalog — light REST convention

> Status: v1 (2026-06)  
> Related: [decision-reactor-invoke.md](./decision-reactor-invoke.md) (session push only), [host-injection.md](./host-injection.md)

No Java SPI — **REST only**. One small convention so external AIPPs (e.g. decision-reactor) can discover worlds and entry templates without Host proxy routes.

## Provider (ontology world AIPP)

Default path prefix on the provider process:

`/api/decision-reactor-invoke`

| Method | Path | Description |
|--------|------|-------------|
| GET | `{prefix}/worlds?env=` | Worlds with a release for `env` |
| GET | `{prefix}/worlds/{worldId}/entry-templates?env=` | Entry-boundary decision templates |
| GET | `{prefix}/worlds/{worldId}/entity-outlines?env=` | (Optional) entity outline text for react preamble |

`env`: `production` \| `staging` (provider normalizes unknown values to `production`).

Entry-template filter: `org.twelve.aipp.invoke.DecisionReactorEntryTemplates` (manual / schedule / ontology activators; exclude `decision` chain steps).

## Consumer

- Store provider **base URL** in AIPP configuration (e.g. `ontology_world.base_url` → `http://127.0.0.1:8093`).
- Call provider REST **directly** for catalog reads.
- Take `env` from Host `PUT /api/host/bindings` when querying catalog (do **not** persist `env` in configuration).
- May expose a local BFF (e.g. `GET /api/catalog/worlds`) for browser widgets that run behind Host app-proxy.

## Response shapes

### GET worlds

```json
{
  "ok": true,
  "env": "production",
  "worlds": [
    { "world_id": "world-eai-onboarding", "world_name": "HR EAI", "release_version": 55 }
  ]
}
```

### GET entry-templates

```json
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

## What Host does not do

Host **must not** expose catalog proxy routes (e.g. `/api/decision-reactor-invoke/worlds` on world-one). Session events still use Host `POST /api/aipp/runtime-events/forward` per [decision-reactor-invoke.md](./decision-reactor-invoke.md).
