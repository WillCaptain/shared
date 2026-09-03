# AIPP Spec Index — Gradual Discovery Router

> **For coding agents:** load **one row** below, implement, then run [`verify.md`](verify.md). Do not bulk-read all spec files.

**Entry point (charter):** [`../skills/aipp-development/SKILL.md`](../skills/aipp-development/SKILL.md)（[`../AGENTS.md`](../AGENTS.md) is a pointer to it）  
**Harness install:** [`../skills/adapters/`](../skills/adapters/) (`aipp-skill-cursor`, `aipp-skill-claude`) — symlinks core into `~/.cursor/skills` / `~/.claude/skills`.  
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
| Host system widgets (`sys.*`) | [`system-widgets.md`](system-widgets.md) | `AippSystemWidgetSpecTest`, `AippWorkProgressSpecTest` |
| Capability tree on Host | [`capability-tree.md`](capability-tree.md) | `GET /api/capability-trees/{app_id}` |
| Imported overlay (virtual `imported` forest) | [`imported-overlay.md`](imported-overlay.md) | `GET /api/imported`, `GET /api/capability-trees/imported` |
| Decoupling fields (lifecycle, events, prompts) | [`host-decoupling.md`](host-decoupling.md) | §6 `assert*` |
| Host shell contributions + shared interface providers | [`host-extensions.md`](host-extensions.md) | `AippHostExtensionSpec` |
| Shared capability provider (memory-one / outline-one) + `requires` | [`capability-providers.md`](capability-providers.md) | Registry warns on unmet `requires` |
| Contacts provider / consumer | [`contacts.md`](contacts.md) + [`capability-providers.md`](capability-providers.md) | `ContactsCapabilitySpec` |
| **Tricky fields** (placement vs refresh) | [`field-semantics.md`](field-semantics.md) | `ToolPlacementTest` |
| Tool placement (`visibility`, `owner_widget`, …) | [`field-semantics.md`](field-semantics.md) + [`host-decoupling.md`](host-decoupling.md) §7 | `ToolPlacementTest` |
| Widget refresh after edits | [`widgets.md`](widgets.md) §5 + [`host-decoupling.md`](host-decoupling.md) §8 | `assertWidgetDeclaresRefreshTool` |
| `POST /api/events` | [`events.md`](events.md) | `assertValidEventSubscriptions` |
| Durable scheduled jobs / handler callbacks | [`scheduler.md`](scheduler.md) | `AippScheduleSpecTest` |
| Host notification publish and lifecycle | [`notifications.md`](notifications.md) | `HostNotificationSpecTest` |
| Host chat runtime（`POST /api/chat` SSE / ChatEvents / `/open`） | [`host-runtime.md`](host-runtime.md) | — Host 实现，AIPP 知晓 |
| Client execution (`execution_surface: client`, ones-shell) | [`client-execution.md`](client-execution.md) | Host + desktop shell |
| **Localization** (session `language`, LocalizedString, user-facing text) | [`localization.md`](localization.md) | `AippLocales`, `assertValidLocalizedLabels` |
| **Host shell style** (theme layers, background, animation sandbox) | [`host-shell-style.md`](host-shell-style.md) | Host manual checklist §7 |
| **Installable theme packages** (`.ones-theme`, animation IR, ZIP safety) | [`theme-packages.md`](theme-packages.md) | `ThemePackageSpec` |
| Client package bootstrap (Once launch install) | [`client-bootstrap.md`](client-bootstrap.md) | `GET /api/client-install/catalog` |
| Session / event / widget 展示标题（`session_summary` 等） | [`display-titles.md`](display-titles.md) | — |
| `sys.configuration` / app settings | [`configuration.md`](configuration.md) | `AippConfigurationSpec` |
| `PUT /api/host/bindings` | [`host-injection.md`](host-injection.md) | `AippHostInjectionSpec` |
| Host URL in app code | [`host-url.md`](host-url.md) | `HostUrlResolverTest` |
| Decision reactor (catalog + push) | [`decision-reactor-integration.md`](decision-reactor-integration.md) | `DecisionReactorEntryTemplatesTest`, `OntologyWorldCatalogSpec` |
| Ontology world capability (Host-brokered `wiki_*`/`ontology_*` tools) | [`ontology-world-capability.md`](ontology-world-capability.md) | Host proxy `POST /api/proxy/tools/{name}` |
| Ontology wiki ops — provider-internal REST (DEPRECATED direct channel) | [`ontology-world-operation.md`](ontology-world-operation.md) | `world-entitir` `/api/worlds/{worldId}/wiki/*` |
| Database access / persistence | [`db-operations.md`](db-operations.md) | `shared/db-ops` SDK (`AtomicDbOps`) |
| Operational trace (UX / integration timeline) | [`trace.md`](trace.md) | `shared/operational-trace-java` + `GET /api/trace/export` |
| User identity (`get_user`) + machine workspace profile | [`user-identity.md`](user-identity.md) | `AippUserIdentitySpec` |
| Function authority (gated tools/skills) | [`function-authority.md`](function-authority.md) | `AippFunctionAuthoritySpec` |
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
| UI stuck in one language / hardcoded 中文 | [`localization.md`](localization.md) — LocalizedString + chat `language` |
| Canvas stale after LLM edit | [`widgets.md`](widgets.md) §5 — `refresh_tool` + `mutates_display` |
| AIPP needs a timer / reminder / scheduled callback | [`scheduler.md`](scheduler.md) — shared `org.twelve.aipp.scheduler`, default 15s |
| Scheduler round overlaps or runs twice | [`scheduler.md`](scheduler.md) §5 — per-level single-flight distributed guard |
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
| [`host-extensions.md`](host-extensions.md) | Declarative top/right banner actions and shared interface providers |
| [`events.md`](events.md) | `POST /api/events` |
| [`scheduler.md`](scheduler.md) | Host-owned durable scheduling + AIPP handler registration/callbacks |
| [`notifications.md`](notifications.md) | Host-owned notification storage + opaque AIPP lifecycle operations |
| [`host-registration.md`](host-registration.md) | Registry install, smoke tests |
| [`host-lifecycle.md`](host-lifecycle.md) | Auto register on launch, deregister on shutdown, Host liveness probe |
| [`system-widgets.md`](system-widgets.md) | `sys.*` |
| [`capability-tree.md`](capability-tree.md) | Tree vs widgets folder |
| [`configuration.md`](configuration.md) | App configuration UI |
| [`host-injection.md`](host-injection.md) | Bindings |
| [`host-url.md`](host-url.md) | URL resolver |
| [`host-runtime.md`](host-runtime.md) | `POST /api/chat` SSE, ChatEvents, `/open`, prompt layers |
| [`display-titles.md`](display-titles.md) | Session / event / widget naming (`session_summary`, `event_label`, `context_title`) |
| [`localization.md`](localization.md) | Session `language` + LocalizedString（用户可见文案必须本地化） |
| [`host-shell-style.md`](host-shell-style.md) | Host shell theme, background image, animation sandbox |
| [`theme-packages.md`](theme-packages.md) | Installable `.ones-theme` ZIP, typed theme documents, integrity, animation IR |
| [`client-execution.md`](client-execution.md) | Client surface + Once executor + `context.env` |
| [`decision-reactor-integration.md`](decision-reactor-integration.md) | Decision reactor: catalog REST + session push |
| [`ontology-world-operation.md`](ontology-world-operation.md) | Wiki provider REST: ensure / nodes / leaves / documents / eval |
| [`db-operations.md`](db-operations.md) | DB access via shared `db-ops` SDK |
| [`trace.md`](trace.md) | Operational trace — cross-surface UX/integration timeline |
| [`capability-providers.md`](capability-providers.md) | Cross-cutting capability providers + `requires` dependency |
| [`verify.md`](verify.md) | `assert*` gate |
| [`llm-config.md`](llm-config.md) | Host LLM provider config (`GET /api/llm-config`) |
| [`user-identity.md`](user-identity.md) | `get_user` + workspace tools |
| [`function-authority.md`](function-authority.md) | Register/assign/check gated tools and skills |

---

## Layer map

```
Tier 0   skills/adapters/* install → harness skills dir (symlink to core); auto-trigger
         — fallback: docs/tier0-bootstrap.prompt.md paste block
Tier 1   skills/aipp-development/SKILL.md (portable charter; AGENTS.md points here)
Tier 1b  skills/aipp-development/references/* (capability / UI / deploy indexes)
Tier 2   spec/INDEX.md (this file)
Tier 3   spec/<topic>.md or docs/*
Tier 4   README.md (changelog + section stubs into spec/)
```
