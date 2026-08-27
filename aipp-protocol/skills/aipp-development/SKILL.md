---
name: aipp-development
description: Develop or modify an AIPP (AI Plugin Program) app or its Host integration. Use when working on anything involving /api/tools, /api/skills, /api/widgets manifests, SKILL.md packages, widget ESM frontends, tool responses (canvas / html_widget / sys.*), host registration or bindings, session policies, runtime event callbacks, capability trees, db-ops persistence, or when bootstrapping a new AIPP HTTP service. Applies to world-entitir, world-one, memory-one, decision-reactor, outline-aipp and any new AIPP. Routes to the right spec page and the Java assert* compliance gate in shared/aipp-protocol — do not guess manifest fields from memory.
---

# AIPP Development — Charter

> **Version**: 2.11 · Canonical **Agent Skills** package for coding agents building AIPP HTTP apps.
> Protocol repo: **`shared/aipp-protocol/`** (paths below are relative to the workspace root that contains `shared/`).
> **Do not load the full `shared/aipp-protocol/README.md` into context.** Use this charter + `spec/INDEX.md` for gradual discovery.
>
> **Packaging:** this directory is harness-agnostic. Install via `skills/adapters/` (`aipp-skill-cursor`, `aipp-skill-claude`, …). Do not treat `~/.claude/skills/` or `~/.cursor/skills/` as source of truth.

## What you are building

An **AIPP** (AI Plugin Program) is a standalone HTTP service that a **Host** (e.g. world-one) discovers and orchestrates:

| Surface | Endpoint | Purpose |
|---------|----------|---------|
| Identity | `GET /api/app` | App manifest |
| Tools | `GET /api/tools`, `POST /api/tools/{name}` | Atomic LLM-callable capabilities |
| Skills | `GET /api/skills`, `GET /api/skills/{name}/playbook` | Multi-step playbooks (progressive disclosure) |
| Widgets | `GET /api/widgets` | UI manifests the Host mounts |

Optional: `GET/PUT /api/configuration`, `PUT /api/host/bindings`, `POST /api/events`.

## Workflow (gradual discovery)

```
1. Classify the task → spec/INDEX.md → load ONE spec file
2. Implement JSON + HTTP handlers (+ ESM if widget UI)
3. Run spec/verify.md (assert* gate)
4. Register / attach on Host → references/deploy.md + spec/host-registration.md
5. README.md is changelog + stubs only — spec/ is the sole normative text
```

**Source of truth:** Java `assert*` in `shared/aipp-protocol/src/main/java/org/twelve/aipp/`. See `spec/verify.md`.

**Tricky fields (`visibility`, `owner_widget`, `router_promoted`, `mutates_display`, `refresh_tool`):**
read `spec/field-semantics.md` **before** editing manifests — placement ≠ side effects ≠ refresh contract.

## Non-negotiable rules

| Rule | Detail |
|------|--------|
| **4 core endpoints** | `GET /api/app`, `GET /api/tools`, `GET /api/widgets`, `POST /api/tools/{name}` |
| **Exactly one main widget** | Per app: exactly one `is_main: true` |
| **`app_id` consistency** | Same kebab-case `app_id` on `/api/app`, `/api/tools.app`, `/api/widgets.app` |
| **Never register `sys.*`** | Return `sys.*` in **tool responses** only — `spec/system-widgets.md` |
| **Tool vs Skill** | One LLM call → Tool. Multi-step → Skill — `spec/skills.md` |
| **Skills served via SDK** | Skills live in `resources/skills/{id}/SKILL.md`; serve all three endpoints via `AippSkillPackages` — never hardcode index entries in Java — `spec/skills.md` §2.1 |
| **No mini-agent on tools** | Tool entries must **not** include `prompt`, `tools[]`, or `resources` |
| **Capability tree** | Routable: `kind: tool` / `skill`. `kind: widget` = catalog only — `spec/capability-tree.md` |
| **Tool placement (v3)** | `visibility` + optional `owner_widget` / `router_promoted` / `mutates_display` — nested `scope` is removed — `spec/host-decoupling.md` §7 |
| **Widget refresh** | Widget: `refresh_tool`; write tools: `mutates_display: true` — not `mutating_tools` on widget — `spec/widgets.md` §5 |
| **Shared UI** | Widgets use Host-loaded `--aipp-*` / `.aipp-*` only — no local CSS — `references/ui-primitives.md` |
| **Cross-app tools** | Depend on **tool names** (`requires` / `allowed-tools`), never provider `app_id` or hardcoded URLs — `references/capability-catalog.md` |
| **DB access** | Persistence goes through the shared `db-ops` SDK (`AtomicDbOps`), one PostgreSQL schema per app — `spec/db-operations.md` |
| **Localization** | User-facing strings use LocalizedString (`en` required); session `language` from Host — `spec/localization.md`. Do **not** hardcode a single language (`*_zh` only is legacy) |
| **Compliance gate** | `spec/verify.md` before done |

## Task router

| If you are… | Read next (only this, under `shared/aipp-protocol/`) |
|-------------|------------------------|
| Bootstrapping a new app | `docs/quickstart-checklist.md` |
| Deploy / Host attach | `skills/aipp-development/references/deploy.md` → `spec/host-lifecycle.md` |
| Shared capabilities (memory, outline, …) | `skills/aipp-development/references/capability-catalog.md` → `spec/capability-providers.md` |
| Widget CSS / tokens | `skills/aipp-development/references/ui-primitives.md` → `spec/widgets.md` §4 |
| Host shell theme / background / animation | `spec/host-shell-style.md` |
| Installable theme package / animation IR | `spec/theme-packages.md` |
| Registering on Host (manual / smoke) | `spec/host-registration.md` |
| Tool manifest + visibility | `spec/field-semantics.md` |
| Localization / i18n labels | `spec/localization.md` |
| Tool HTTP responses | `spec/tool-responses.md` |
| Skills + SKILL.md | `spec/skills.md` |
| Widget manifest + ESM UI | `spec/widgets.md` |
| Widget refresh after edits | `spec/widgets.md` §5 + `spec/host-decoupling.md` §8 |
| **Tricky manifest fields** | `spec/field-semantics.md` (**start here**) |
| Sessions / canvas open | `spec/sessions.md` |
| `sys.*` in responses | `spec/system-widgets.md` |
| Capability tree | `spec/capability-tree.md` |
| Host events | `spec/events.md` |
| Configuration UI | `spec/configuration.md` |
| Host bindings | `spec/host-injection.md` |
| LLM provider config (Host) | `spec/llm-config.md` |
| Host URL in app code | `spec/host-url.md` |
| Persisting data / database | `spec/db-operations.md` |
| Function authority (gated tools/skills) | `spec/function-authority.md` |
| Decision reactor integration | `spec/decision-reactor-integration.md` |
| Before PR | `spec/verify.md` |
| Full index | `spec/INDEX.md` |

## Package layout

```
skills/
  aipp-development/          ← this package (portable Agent Skills standard)
    SKILL.md
    references/
  adapters/
    aipp-skill-cursor/       ← install → ~/.cursor/skills/aipp-development
    aipp-skill-claude/       ← install → ~/.claude/skills/aipp-development
```

## What this is NOT

Host `/api/skills` is for **end-user** business playbooks at runtime — not for teaching coding agents the protocol. This development skill is a repo/harness artifact and must never be served by `worldone-system` or any app's skills endpoint.
