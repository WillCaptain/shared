# Ontology world operation — wiki provider REST

> Status: **DEPRECATED (2026-06)** — superseded by the Host-brokered
> [`ontology-world-capability.md`](./ontology-world-capability.md). New consumers MUST reach the
> ontology world **by tool name through the Host** and MUST NOT configure a provider base URL.
> This document remains as the **provider-internal** description of the REST endpoints that the
> brokered `wiki_*` / `ontology_*` tools wrap; it is retained for backward compatibility only.
> Sibling of [decision-reactor-integration.md](./decision-reactor-integration.md)
> Provider: `world-entitir`

A direct, **non-Host-proxied** REST channel between an ontology world **provider**
(`world-entitir`) and a wiki **consumer** (e.g. `note-one`). It lets a consumer build
and read a user's knowledge wiki that is modeled as an entitir ontology:

- **Route node** — an ontology with navigation edges only (no `document` column).
- **Leaf node** — an ontology carrying a `document` column (markdown knowledge body).
- **Singleton** — an ontology with exactly one (current) row, bound as a single record
  (`let resume = __ontology_repo__<Resume>`) rather than a collection. Authored with
  `"~singleton": "true"`; leaf marker authored with `"~document": "true"`.
- **Versioned singleton** — a copy-on-write singleton authored with `"~versioned": "true"`.
  It carries reserved `ver_no` / `ver_current` columns; the binding resolves the **current**
  version, each write inserts a new version (superseding the prior), and `/history` lists all.

| Concern | Owner |
|---------|--------|
| `env` (runtime) | Host `PUT /api/host/bindings` only — never AIPP configuration |
| Wiki world id | The **user id** (or `"my note"` when no user info) |
| Provider base URL | Consumer configuration (`ontology_world.base_url`) |

---

## 1. Convention

No Java SPI — **REST only**, no Host proxy. Path prefix on the provider process:
`/api/worlds/{worldId}/wiki`. `worldId` is the user id.

`env`: `staging` | `production` (provider normalizes unknown values to `production`).

| Method | Path | Description |
|--------|------|-------------|
| POST | `{prefix}/ensure` | Create the user's wiki world if missing. Body `{ "name"? }` (defaults to `"my note"`). |
| POST | `{prefix}/nodes` | Upsert a node. Body `{ "entity_type", "fields": {…}, "id"?, "env"? }`. Leaf markdown goes in `fields.document`. Plain singletons update in place; **versioned** singletons write a new version (copy-on-write); collections update by `id` when given, else create a row. |
| GET | `{prefix}/leaves?env=` | Traverse all leaf entity types; returns each type's markdown bodies. |
| GET | `{prefix}/documents?type=&id=&env=` | One leaf's markdown (`id` required for collection leaves; omitted for singletons). |
| POST | `{prefix}/eval` | Generic bounded outline eval. Body `{ "expr", "env"? }`. Covers traverse / aggregate / compute / read. |

### Schema evolution + history

| Method | Path | Description |
|--------|------|-------------|
| GET | `{prefix}/schema?env=` | Type catalog: `[{ entity_type, is_leaf, is_singleton, is_versioned, parent_ontology, fields }]`. Drives the consumer's placement router. |
| POST | `{prefix}/types` | Add/modify type definitions. Body `{ "definitions": [<Entitir JSON>…], "build"?, "env"? }`. Each definition may carry `~singleton`/`~document`/`~versioned`. `build:true` builds staging immediately. Proxies `world_add_definition` (+`world_build`). |
| POST | `{prefix}/build` | Build the staging world from current definitions (proxies `world_build`). |
| POST | `{prefix}/promote` | Promote staging → production. Body `{ "confidence"?, "threshold"?, "confirmed"? }`. **Confidence-gated**: when `confidence` < `threshold` (default `0.8`) and not `confirmed`, the promotion is withheld and `needs_confirmation:true` is returned. Proxies `world_promote`. |
| GET | `{prefix}/history?type=&env=` | All versions of a versioned singleton, newest first (`{ versions: [ {ver_no, ver_current, …} ] }`). |

`/eval` is the single read primitive; the convenience reads/writes wrap it. Structure
endpoints wrap the existing world build/promote pipeline.

### Two-axis write contract

- **CONTENT** (instance data via `/nodes`, `/eval`) is always safe and applied immediately.
- **STRUCTURE** (schema evolution via `/types` → `/build` → `/promote`) is gated by
  `confidence`: high-confidence changes auto-promote; low-confidence ones return
  `needs_confirmation` so the consumer can ask the user. Type/field **removal** rebuilds with
  `dropOrphanTables` and must always be explicitly confirmed (never auto-promoted).

### Response shapes

```json
// POST /ensure
{ "ok": true, "world_id": "user-123", "name": "my note", "created": true }
```

```json
// GET /leaves
{
  "ok": true, "world_id": "user-123", "env": "production",
  "leaves": [
    { "entity_type": "Article", "singleton": false, "documents": "[\"# Title\\n…\"]" }
  ]
}
```

```json
// POST /eval  (and /documents)
{ "ok": true, "world_id": "user-123", "env": "production",
  "expression": "articles.map(x -> x.document).to_list()", "result": "…" }
```

```json
// POST /nodes  (versioned singleton write)
{ "ok": true, "world_id": "user-123", "env": "production",
  "entity_type": "Resume", "version": 4, "id": 12 }
```

```json
// POST /promote  (gated, below threshold)
{ "ok": true, "world_id": "user-123", "promoted": false,
  "needs_confirmation": true, "confidence": 0.6, "threshold": 0.8 }
```

Errors: `{ "ok": false, "error": "…", "expression"? }` with HTTP 400.

---

## 2. Consumer rules

- Store provider **base URL** in AIPP configuration (`ontology_world.base_url` →
  `http://127.0.0.1:8093`). Call provider REST **directly**.
- Take `env` from Host `PUT /api/host/bindings` (do **not** persist `env` in configuration).
- Use `worldId = user_id`; fall back to a stable id with name `"my note"` when no user.
- Treat `result` as opaque text suitable for LLM summarization; for structured needs,
  shape the projection in the `expr` you send to `/eval`.

## 3. Consumer write path (note-one)

The consumer **owns its analysis LLM** (calls the provider directly). As of protocol **v2.9**, the effective provider config MUST be fetched from the Host — see [`llm-config.md`](llm-config.md) — using the stable `user_id` from [`user-identity.md`](user-identity.md). All conversation turns and uploaded files are sent to
the consumer, which analyzes them and emits wiki ops, then applies them through this protocol:

- **Content ops** → `/nodes` (and `/eval`): `UPSERT_LEAF, PATCH_LEAF, SET_FIELD, ADD_ROUTE, MOVE_LEAF`.
- **Structure ops** → `/types` → `/build` → gated `/promote`: `PROPOSE_TYPE, ADD_FIELD` (each carries a `confidence`).

Consumer tools (note-one): `put_note` (explicit), `wiki_consolidate` (post-turn turns + files),
`wiki_graduate` (memory-one bridge), `attach_file` (knowledge attachment), `wiki_graph` (main widget data).
Consumer config keys:

| Key | Purpose |
|-----|---------|
| `note-one.ontology-world-base-url` | provider base URL (this protocol) |
| ~~`note-one.llm.*`~~ | **Deprecated (v2.9)** — use Host [`llm-config.md`](llm-config.md) |
| `note-one.memory-one-base-url` | bridge source: `POST /api/tools/memory_query` for stabilized memories |
| `note-one.promote-threshold` | structure auto-promote threshold (default `0.8`) |
| `note-one.public-base-url` | externally reachable base for attachment links (blank → relative) |

### Attachments + knowledge graph (consumer-side)

- **Attachments** — `attach_file` stores the upload in the consumer (`note_one_attachment`), serves it
  at `GET /api/attachments/{id}`, and the analysis LLM embeds a markdown reference
  (`![name](url)` / `[name](url)`) into the relevant leaf's `document`. The knowledge thus stays in
  the wiki markdown; the provider needs no attachment concept.
- **Knowledge graph** — `wiki_graph` builds the main widget's graph purely from provider reads
  (`/schema` for nodes + route edges via `parent_ontology`, `/leaves` for each leaf's `document`).
  Distinct from the provider's schema graph: nodes carry the actual markdown bodies.

### Short-term-memory upgrade (memory-one bridge)

`wiki_graduate` pulls stabilized memories from memory-one (`memory_query`, scope `GLOBAL`,
importance floor), skips already-graduated ids via a `note_one_graduated` ledger (idempotent),
lets the consumer LLM fold the new ones into the wiki, then marks them graduated. memory-one
remains the short/medium-term working store; the wiki is the durable, curated long-term store.

## 4. Independence rules

- Consumer **may** store the provider base URL in configuration.
- Provider **must not** import consumer types.
- Host **must not** expose wiki-operation proxy routes.
