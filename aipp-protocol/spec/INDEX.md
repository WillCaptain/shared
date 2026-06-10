# AIPP Spec Index — Gradual Discovery Router

> **For coding agents:** load **one row** below, implement, then run [`verify.md`](verify.md). Do not bulk-read all spec files.

**Entry point:** [`../AGENTS.md`](../AGENTS.md)  
**Full encyclopedia (last resort):** [`../README.md`](../README.md)

---

## By task

| Task | Read first | Then verify |
|------|------------|-------------|
| New AIPP app from scratch | [`../docs/quickstart-checklist.md`](../docs/quickstart-checklist.md) | [`verify.md`](verify.md) § Minimum gate |
| Register on Host / smoke test | [`host-registration.md`](host-registration.md) | Chat + registry list |
| `GET /api/app` manifest | README §2 / §7.1 | `assertValidAppManifest` |
| `GET /api/tools` manifest | README §3 + [`host-decoupling.md`](host-decoupling.md) | `assertValidToolsApiStructure` |
| `POST /api/tools/{name}` + responses | [`tool-responses.md`](tool-responses.md) | Response `assert*` |
| `GET /api/skills` + SKILL.md | [`skills.md`](skills.md) | `assertValidSkillsApiStructure` |
| `GET /api/widgets` + ESM frontend | [`widgets.md`](widgets.md) | `AippWidgetSpec` |
| Canvas / app sessions | [`sessions.md`](sessions.md) | `assertValidSkillSessionExtension`, `assertCanvasOpenWithNewSession` |
| Host system widgets (`sys.*`) | [`system-widgets.md`](system-widgets.md) | `AippSystemWidgetSpecTest` |
| Capability tree on Host | [`capability-tree.md`](capability-tree.md) | `GET /api/capability-trees/{app_id}` |
| Imported overlay (virtual `imported` forest) | [`imported-overlay.md`](imported-overlay.md) | `GET /api/imported`, `GET /api/capability-trees/imported` |
| Decoupling fields (lifecycle, events, prompts) | [`host-decoupling.md`](host-decoupling.md) | §6 `assert*` |
| **Tricky fields** (placement vs refresh) | [`field-semantics.md`](field-semantics.md) | `ToolPlacementTest` |
| Tool placement (`visibility`, `owner_widget`, …) | [`field-semantics.md`](field-semantics.md) + [`host-decoupling.md`](host-decoupling.md) §7 | `ToolPlacementTest` |
| Widget refresh after edits | [`widgets.md`](widgets.md) §5 + [`host-decoupling.md`](host-decoupling.md) §8 | `assertWidgetDeclaresRefreshTool` |
| `POST /api/events` | [`events.md`](events.md) | `assertValidEventSubscriptions` |
| `sys.configuration` / app settings | [`configuration.md`](configuration.md) | `AippConfigurationSpec` |
| `PUT /api/host/bindings` | [`host-injection.md`](host-injection.md) | `AippHostInjectionSpec` |
| Host URL in app code | [`host-url.md`](host-url.md) | `HostUrlResolverTest` |
| Decision reactor | [`decision-reactor-invoke.md`](decision-reactor-invoke.md) | `DecisionReactorEntryTemplatesTest` |
| Ontology world catalog | [`ontology-world-catalog.md`](ontology-world-catalog.md) | `OntologyWorldCatalogSpec` |
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
| Install fails on Host | [`host-registration.md`](host-registration.md) |
| Widget button does nothing | [`widgets.md`](widgets.md) — `hostApi.callTool` |
| Canvas stale after LLM edit | [`widgets.md`](widgets.md) §5 — `refresh_tool` + `mutates_display` |
| `mutating_tools` / `refresh_skill` rejected in review | Migrate to v2.7 — [`verify.md`](verify.md) §2.4–2.7 |
| Widget tool visible in main chat | [`host-decoupling.md`](host-decoupling.md) §7 — set `owner_widget` |

---

## Spec files

| File | Topic |
|------|--------|
| [`widgets.md`](widgets.md) | Manifest, ESM, hostApi, views, `refresh_tool`, theme, upload |
| [`tool-responses.md`](tool-responses.md) | `_context`, envelopes, status, priority |
| [`skills.md`](skills.md) | Index, SKILL.md, tool vs skill |
| [`sessions.md`](sessions.md) | `new_session`, `session_policy`, titles |
| [`field-semantics.md`](field-semantics.md) | **Design commentary** for placement / `mutates_display` / `refresh_tool` |
| [`host-decoupling.md`](host-decoupling.md) | lifecycle, tool placement, widget refresh, events, prompts |
| [`events.md`](events.md) | `POST /api/events` |
| [`host-registration.md`](host-registration.md) | Registry install, smoke tests |
| [`system-widgets.md`](system-widgets.md) | `sys.*` |
| [`capability-tree.md`](capability-tree.md) | Tree vs widgets folder |
| [`configuration.md`](configuration.md) | App configuration UI |
| [`host-injection.md`](host-injection.md) | Bindings |
| [`host-url.md`](host-url.md) | URL resolver |
| [`decision-reactor-invoke.md`](decision-reactor-invoke.md) | Decision reactor |
| [`ontology-world-catalog.md`](ontology-world-catalog.md) | World catalog |
| [`verify.md`](verify.md) | `assert*` gate |

---

## Layer map

```
Tier 0   docs/tier0-bootstrap.prompt.md
Tier 1   AGENTS.md
Tier 2   spec/INDEX.md (this file)
Tier 3   spec/<topic>.md or docs/*
Tier 4   README.md §N (only when stuck)
```
