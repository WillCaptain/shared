# Decision session push — consumer ↔ Host ↔ provider

> Status: v2 (2026-06)  
> Related: [ontology-world-catalog.md](./ontology-world-catalog.md), [host-injection.md](./host-injection.md)

Covers **OntologySessionChangeEvent** delivery and consumer dispatch policy only.

**Catalog** (world list, entry templates) is **not** Host-proxied — see [ontology-world-catalog.md](./ontology-world-catalog.md).

## Roles

| Concern | Owner |
|---------|--------|
| `env` (runtime) | Host `PUT /api/host/bindings` only — never AIPP configuration |
| World list / entry templates | Consumer → provider REST; provider base URL in consumer configuration |
| `OntologySessionChangeEvent` delivery | Provider → Host → consumer callback (push) |
| Session listener scope | Consumer registers `{world_id, entry_templates[]}` internally |

### Consumer (decision-reactor)

```json
{
  "runtime_event_callbacks": [
    {
      "events": ["ontology_session_change"],
      "path": "/api/worlds/{worldId}/session-change"
    }
  ]
}
```

### Host (world-one)

| Endpoint | Action |
|----------|--------|
| `POST /api/aipp/runtime-events/forward` | Route push events to registered `runtime_event_callbacks` |

Host **does not** proxy ontology catalog routes.

## Entry template filter (catalog)

See [ontology-world-catalog.md](./ontology-world-catalog.md) — shared helper `DecisionReactorEntryTemplates`.

## Notification policy (runtime)

When world-entitir flushes `OntologySessionChangeEvent`, the payload includes:

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

## Push: ontology_session_change

Provider → Host:

```http
POST {host_base_url}/api/aipp/runtime-events/forward
{
  "event_name": "ontology_session_change",
  "world_id": "world-eai-onboarding",
  "env": "production",
  "event": { ... }
}
```

Host → consumer:

```http
POST {consumer}/api/worlds/{worldId}/session-change
{
  "env": "production",
  "event": { ... }
}
```

Consumer dispatches react when:

1. `env` matches Host bindings  
2. A registered session exists for `world_id`  
3. `payload.root_template_id` ∈ registered `entry_templates`  
4. Notification policy passes  

## Independence rules

- Consumer **may** store ontology provider base URL in configuration for catalog only  
- Consumer **must not** poll provider `/runtime-events`  
- Provider **must not** import consumer types  
- Session events **must** go through Host forward (not direct provider → consumer for push)
