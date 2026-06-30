# Ontology world capability — Host-brokered tools

> Status: v1 (2026-06) · **Supersedes the direct channel in [`ontology-world-operation.md`](./ontology-world-operation.md)**
> Provider: `world-entitir` (registers as a normal AIPP) · Pattern: [`capability-providers.md`](./capability-providers.md)

The ontology world (build & read a user's knowledge wiki, list worlds / entry templates,
subscribe to session changes) is a **shared capability**, reached the same way as any other
capability provider: **by tool name, through the Host**. Consumers (`note-one`, `decision-reactor`)
**never know the provider's base URL** and depend only on the tool **names**.

This replaces the older *direct, consumer-configures-`ontology_world.base_url`* REST channel.
There is no `ontology_world.base_url` in a consumer anymore.

## 1. How it works (no new framework)

Exactly the capability-provider pattern (`capability-providers.md`):

1. The provider (`world-entitir`) registers with the Host (`POST {host}/api/registry/install`)
   and exposes the tools below in its `GET /api/tools` with global, stable names.
2. The Host indexes each tool name → provider `base_url` (`AppRegistry.findAppForTool`).
3. A consumer calls `POST {host}/api/proxy/tools/{name}` with `{ "args": { … } }`; the Host
   routes by **name** to the provider. The consumer holds only the **Host** URL.
4. Consumers declare the names they need at their `/api/tools` root via `requires: [...]`.

The provider can be renamed/replaced without touching any consumer.

## 2. Capability tool surface

`world_id` is the user id. `env` is `staging | production` (provider normalizes unknown → production).
Every tool returns `{ ok, world_id, … }` (or `{ ok:false, error }`).

### Wiki ops (build & read a user's wiki world)

| Tool name | Args | Purpose |
|-----------|------|---------|
| `wiki_ensure` | `{world_id, name?}` | Create the user's wiki world if missing. |
| `wiki_upsert_node` | `{world_id, entity_type, fields, id?, env?}` | Upsert a route/leaf node; leaf markdown in `fields.document`. |
| `wiki_leaves` | `{world_id, env?}` | Traverse leaf types → each type's markdown bodies. |
| `wiki_document` | `{world_id, type, id?, env?}` | One leaf's markdown (`id` for collection leaves). |
| `wiki_eval` | `{world_id, expr, env?}` | Generic bounded Outline eval (traverse/aggregate/compute/read). |
| `wiki_schema` | `{world_id, env?}` | Type catalog (`entity_type, is_leaf, is_singleton, is_versioned, parent_ontology, fields`). |
| `wiki_add_types` | `{world_id, definitions[], build?, env?}` | Add/modify Entitir type definitions (`~singleton/~document/~versioned`). |
| `wiki_build` | `{world_id}` | Build staging from current definitions. |
| `wiki_promote` | `{world_id, confidence?, threshold?, confirmed?}` | Promote staging→production (confidence-gated; returns `needs_confirmation`). |
| `wiki_history` | `{world_id, type, env?}` | All versions of a versioned singleton, newest first. |

### Catalog + subscription ops (decision reactor)

| Tool name | Args | Purpose |
|-----------|------|---------|
| `ontology_list_worlds` | `{env?}` | Worlds that have releases. |
| `ontology_entry_templates` | `{world_id, env?}` | Entry-boundary decision templates for a world. |
| `ontology_entity_outlines` | `{world_id, env?}` | Entity outline text / entity types for the reactor preamble. |
| `ontology_subscribe` | `{world_id, template_id, subscriber_id, callback_url}` | Subscribe to session changes (push to `callback_url`). |
| `ontology_unsubscribe` | `{world_id, subscriber_id}` | Remove a session-change subscriber. |

> **Two-axis write contract is unchanged** (from `ontology-world-operation.md`): CONTENT
> (`wiki_upsert_node`/`wiki_eval`) applies immediately; STRUCTURE (`wiki_add_types`→`wiki_build`→
> `wiki_promote`) is confidence-gated. Type/field removal must always be explicitly confirmed.

> **Callbacks are still direct.** `ontology_subscribe.callback_url` is a URL the provider pushes to
> (e.g. `{consumer}/api/worlds/{worldId}/session-change`). Only the *outbound* calls are brokered;
> the provider→consumer push needs a reachable consumer URL (loopback or public base).

## 3. Consumer rules

- Hold only the **Host** base URL. **Do not** configure or store the provider's URL.
- Call tools by name via the Host proxy; depend on names, not on the `world-entitir` `app_id`.
- Declare `requires: ["wiki_ensure", "wiki_upsert_node", …]` at your `/api/tools` root so the Host
  warns at install time if the capability is not yet present.
- Degrade gracefully: if a required tool is unresolved (provider not installed / down), tell the
  user the ontology world is unavailable rather than failing silently.

## 4. Migration from the direct channel

| Before (`ontology-world-operation.md`) | After (this spec) |
|----------------------------------------|-------------------|
| `POST {provider}/api/worlds/{id}/wiki/ensure` | `POST {host}/api/proxy/tools/wiki_ensure {args:{world_id}}` |
| `GET {provider}/api/worlds/{id}/wiki/leaves` | `POST {host}/api/proxy/tools/wiki_leaves {args:{world_id}}` |
| `GET {provider}/api/decision-reactor-invoke/worlds` | `POST {host}/api/proxy/tools/ontology_list_worlds {args:{}}` |
| consumer config `ontology_world.base_url` | **removed** |

The direct `/api/worlds/{worldId}/wiki/*` and `/api/decision-reactor-invoke/*` REST endpoints MAY
remain on the provider for backward compatibility, but new consumers MUST use the brokered tools.
