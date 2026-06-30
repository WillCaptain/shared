# AIPP Spec Index — Gradual Discovery Router

> **For coding agents:** load **one row** below, implement, then run [`verify.md`](verify.md). Do not bulk-read all spec files.

**Entry point (charter):** [`../skills/aipp-development/SKILL.md`](../skills/aipp-development/SKILL.md)（[`../AGENTS.md`](../AGENTS.md) is a pointer to it）  
**[`../README.md`](../README.md)** is changelog + section stubs only — all normative text lives in `spec/*.md`.

---

## By task

| Task | Read first | Then verify |
|------|------------|-------------|
| New AIPP app from scratch | [`../docs/quickstart-checklist.md`](../docs/quickstart-checklist.md) | [`verify.md`](verify.md) § Minimum gate |
| Register on Host / smoke test | [`host-registration.md`](host-registration.md) | Chat + registry list |
| Auto register/deregister on launch/shutdown + liveness | [`host-lifecycle.md`](host-lifecycle.md) | `aipp-protocol-spring` + `aipp.*` config |
| `GET /api/app` manifest | [`app-manifest.md`](app-manifest.md) | `assertValidAppManifest` |
| `GET /api/tools` manifest | [`tool-manifest.md`](tool-manifest.md) + [`host-decoupling.md`](host-decoupling.md) | `assertValidToolsApiStructure` |
| `POST /api/tools/{name}` + responses | [`tool-responses.md`](tool-responses.md) | Response `assert*` |
| `GET /api/skills` + SKILL.md | [`skills.md`](skills.md) | `assertValidSkillsApiStructure` |
| `GET /api/widgets` + ESM frontend | [`widgets.md`](widgets.md) | `AippWidgetSpec` |
| Canvas / app sessions | [`sessions.md`](sessions.md) | `assertValidSkillSessionExtension`, `assertCanvasOpenWithNewSession` |
| Host system widgets (`sys.*`) | [`system-widgets.md`](system-widgets.md) | `AippSystemWidgetSpecTest` |
| Capability tree on Host | [`capability-tree.md`](capability-tree.md) | `GET /api/capability-trees/{app_id}` |
| Imported overlay (virtual `imported` forest) | [`imported-overlay.md`](imported-overlay.md) | `GET /api/imported`, `GET /api/capability-trees/imported` |
| Decoupling fields (lifecycle, events, prompts) | [`host-decoupling.md`](host-decoupling.md) | §6 `assert*` |
| Shared capability provider (memory-one / outline-one) + `requires` | [`capability-providers.md`](capability-providers.md) | Registry warns on unmet `requires` |
| **Tricky fields** (placement vs refresh) | [`field-semantics.md`](field-semantics.md) | `ToolPlacementTest` |
| Tool placement (`visibility`, `owner_widget`, …) | [`field-semantics.md`](field-semantics.md) + [`host-decoupling.md`](host-decoupling.md) §7 | `ToolPlacementTest` |
| Widget refresh after edits | [`widgets.md`](widgets.md) §5 + [`host-decoupling.md`](host-decoupling.md) §8 | `assertWidgetDeclaresRefreshTool` |
| `POST /api/events` | [`events.md`](events.md) | `assertValidEventSubscriptions` |
| Host chat runtime（`POST /api/chat` SSE / ChatEvents / `/open`） | [`host-runtime.md`](host-runtime.md) | — Host 实现，AIPP 知晓 |
| Client execution (`execution_surface: client`, ones-shell) | [`client-execution.md`](client-execution.md) | Host + desktop shell |
| Client package bootstrap (Once launch install) | [`client-bootstrap.md`](client-bootstrap.md) | `GET /api/client-install/catalog` |
| Session / event / widget 展示标题（`session_summary` 等） | [`display-titles.md`](display-titles.md) | — |
| `sys.configuration` / app settings | [`configuration.md`](configuration.md) | `AippConfigurationSpec` |
| `PUT /api/host/bindings` | [`host-injection.md`](host-injection.md) | `AippHostInjectionSpec` |
| Host URL in app code | [`host-url.md`](host-url.md) | `HostUrlResolverTest` |
| Decision reactor (catalog + push) | [`decision-reactor-integration.md`](decision-reactor-integration.md) | `DecisionReactorEntryTemplatesTest`, `OntologyWorldCatalogSpec` |
| Ontology world capability (Host-brokered `wiki_*`/`ontology_*` tools) | [`ontology-world-capability.md`](ontology-world-capability.md) | Host proxy `POST /api/proxy/tools/{name}` |
| Ontology wiki ops — provider-internal REST (DEPRECATED direct channel) | [`ontology-world-operation.md`](ontology-world-operation.md) | `world-entitir` `/api/worlds/{worldId}/wiki/*` |
| Database access / persistence | [`db-operations.md`](db-operations.md) | `shared/db-ops` SDK (`AtomicDbOps`) |
| User identity (`get_user`) + machine workspace profile | [`user-identity.md`](user-identity.md) | `AippUserIdentitySpec` |
| LLM provider config (Host `GET /api/llm-config`) | [`llm-config.md`](llm-config.md) | `AippLlmConfigSpec` (planned) |
| LLM config rollout (cross-repo) | [`../docs/llm-config-migration.md`](../docs/llm-config-migration.md) | Phase checklist |
| Compliance before merge | [`verify.md`](verify.md) | All applicable `assert*` |

---

## By symptom

| Symptom | Likely doc |
|---------|------------|
| “Can I register `sys.selection`?” | [`system-widgets.md`](system-widgets.md) — **No** |
| Router finds widget instead of tool | [`capability-tree.md`](capability-tree.md) |
| Skill not discovered | [`skills.md`](skills.md) — WHEN clause + `allowed_tools` |
| Wrong UI mode (chat vs canvas vs pop) | [`tool-responses.md`](tool-responses.md) § priority |
| Duplicate task panel rows | [`sessions.md`](sessions.md) — `session_policy` |
| Multiple `is_main` widgets | [`verify.md`](verify.md) |
| `app_id` mismatch | [`verify.md`](verify.md) |
| Tool rejected at Host startup | No `prompt`/`tools[]`/`resources` on tools — [`skills.md`](skills.md) |
| Env / Host URL in configuration | [`host-injection.md`](host-injection.md) |
| LLM API key / model / base URL | [`llm-config.md`](llm-config.md) — **not** in AIPP `configuration` or bindings |
| Install fails on Host | [`host-registration.md`](host-registration.md) |
| Skill's tool from another app "not found" / shared capability | [`capability-providers.md`](capability-providers.md) — depend on tool name + `requires` |
| Widget button does nothing | [`widgets.md`](widgets.md) — `hostApi.callTool` |
| Canvas stale after LLM edit | [`widgets.md`](widgets.md) §5 — `refresh_tool` + `mutates_display` |
| `mutating_tools` / `refresh_skill` / `is_canvas_mode` rejected | Removed in v2.8 — [`verify.md`](verify.md) § Protocol compression |
| Widget tool visible in main chat | [`host-decoupling.md`](host-decoupling.md) §7 — set `owner_widget` |

---

## Spec files

| File | Topic |
|------|--------|
| [`app-manifest.md`](app-manifest.md) | `GET /api/app`, endpoint overview, `main_widget_type` / `sys.app-info` |
| [`tool-manifest.md`](tool-manifest.md) | `GET /api/tools` — entry structure, compat layer, extension fields |
| [`widgets.md`](widgets.md) | Manifest, ESM, hostApi, views, `refresh_tool`, theme, upload |
| [`tool-responses.md`](tool-responses.md) | `_context`, envelopes, status, priority |
| [`skills.md`](skills.md) | Index, SKILL.md, tool vs skill |
| [`sessions.md`](sessions.md) | `new_session`, `session_policy`, titles |
| [`field-semantics.md`](field-semantics.md) | **Design commentary** for placement / `mutates_display` / `refresh_tool` |
| [`host-decoupling.md`](host-decoupling.md) | lifecycle, tool placement, widget refresh, events, prompts |
| [`events.md`](events.md) | `POST /api/events` |
| [`host-registration.md`](host-registration.md) | Registry install, smoke tests |
| [`host-lifecycle.md`](host-lifecycle.md) | Auto register on launch, deregister on shutdown, Host liveness probe |
| [`system-widgets.md`](system-widgets.md) | `sys.*` |
| [`capability-tree.md`](capability-tree.md) | Tree vs widgets folder |
| [`configuration.md`](configuration.md) | App configuration UI |
| [`host-injection.md`](host-injection.md) | Bindings |
| [`host-url.md`](host-url.md) | URL resolver |
| [`host-runtime.md`](host-runtime.md) | `POST /api/chat` SSE, ChatEvents, `/open`, prompt layers |
| [`display-titles.md`](display-titles.md) | Session / event / widget naming (`session_summary`, `event_label`, `context_title`) |
| [`decision-reactor-integration.md`](decision-reactor-integration.md) | Decision reactor: catalog REST + session push |
| [`ontology-world-operation.md`](ontology-world-operation.md) | Wiki provider REST: ensure / nodes / leaves / documents / eval |
| [`db-operations.md`](db-operations.md) | DB access via shared `db-ops` SDK |
| [`capability-providers.md`](capability-providers.md) | Cross-cutting capability providers + `requires` dependency |
| [`verify.md`](verify.md) | `assert*` gate |
| [`llm-config.md`](llm-config.md) | Host LLM provider config (`GET /api/llm-config`) |
| [`user-identity.md`](user-identity.md) | `get_user` + workspace tools |

---

## Layer map

```
Tier 0   skills/aipp-development/ installed in the agent harness (auto-trigger)
         — fallback: docs/tier0-bootstrap.prompt.md paste block
Tier 1   skills/aipp-development/SKILL.md (charter; AGENTS.md points here)
Tier 2   spec/INDEX.md (this file)
Tier 3   spec/<topic>.md or docs/*
Tier 4   README.md (changelog + section stubs into spec/)
```
