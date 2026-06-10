# AIPP — Coding Agent Guide

> **Version**: 2.7 · **Audience**: Codex, Claude Code, Cursor, and other **coding agents** building AIPP HTTP apps.
>
> **Do not load the full [`README.md`](README.md) into context.** Use this file + [`spec/INDEX.md`](spec/INDEX.md) for gradual discovery.

---

## What you are building

An **AIPP** (AI Plugin Program) is a standalone HTTP service that a **Host** (e.g. world-one) discovers and orchestrates:

| Surface | Endpoint | Purpose |
|---------|----------|---------|
| Identity | `GET /api/app` | App manifest |
| Tools | `GET /api/tools`, `POST /api/tools/{name}` | Atomic LLM-callable capabilities |
| Skills | `GET /api/skills`, `GET /api/skills/{name}/playbook` | Multi-step playbooks (progressive disclosure) |
| Widgets | `GET /api/widgets` | UI manifests the Host mounts |

Optional: `GET/PUT /api/configuration`, `PUT /api/host/bindings`, `POST /api/events`.

---

## Workflow (gradual discovery)

```
1. Classify the task → spec/INDEX.md → load ONE spec file
2. Implement JSON + HTTP handlers (+ ESM if widget UI)
3. Run spec/verify.md (assert* gate)
4. Register on Host → spec/host-registration.md smoke test
5. README §N only if INDEX sends you there
```

**Source of truth:** Java `assert*` in this module. See [`spec/verify.md`](spec/verify.md).

**Tricky fields (`visibility`, `owner_widget`, `router_shortcut`, `mutates_display`, `refresh_tool`):**  
Read [`spec/field-semantics.md`](spec/field-semantics.md) **before** editing manifests — placement ≠ side effects ≠ refresh contract.

---

## Non-negotiable rules

| Rule | Detail |
|------|--------|
| **4 core endpoints** | `GET /api/app`, `GET /api/tools`, `GET /api/widgets`, `POST /api/tools/{name}` |
| **Exactly one main widget** | Per app: exactly one `is_main: true` |
| **`app_id` consistency** | Same kebab-case `app_id` on `/api/app`, `/api/tools.app`, `/api/widgets.app` |
| **Never register `sys.*`** | Return `sys.*` in **tool responses** only — [`spec/system-widgets.md`](spec/system-widgets.md) |
| **Tool vs Skill** | One LLM call → Tool. Multi-step → Skill — [`spec/skills.md`](spec/skills.md) |
| **No mini-agent on tools** | Tool entries must **not** include `prompt`, `tools[]`, or `resources` |
| **Capability tree** | Routable: `kind: tool` / `skill`. `kind: widget` = catalog only — [`spec/capability-tree.md`](spec/capability-tree.md) |
| **Tool placement (v3)** | `visibility` + optional `owner_widget` / `router_shortcut` / `mutates_display` — not nested `scope` — [`spec/host-decoupling.md`](spec/host-decoupling.md) §7 |
| **Widget refresh** | Widget: `refresh_tool`; write tools: `mutates_display: true` — not `mutating_tools` on widget — [`spec/widgets.md`](spec/widgets.md) §5 |
| **Compliance gate** | [`spec/verify.md`](spec/verify.md) before done |

Compressed rules: README §14. Anti-patterns: README §16.

---

## Task router

| If you are… | Read next (only this) |
|-------------|------------------------|
| Bootstrapping a new app | [`docs/quickstart-checklist.md`](docs/quickstart-checklist.md) |
| Registering on Host | [`spec/host-registration.md`](spec/host-registration.md) |
| Tool manifest + visibility | [`spec/field-semantics.md`](spec/field-semantics.md) + README §3 |
| Tool HTTP responses | [`spec/tool-responses.md`](spec/tool-responses.md) |
| Skills + SKILL.md | [`spec/skills.md`](spec/skills.md) |
| Widget manifest + ESM UI | [`spec/widgets.md`](spec/widgets.md) |
| Widget refresh after edits | [`spec/widgets.md`](spec/widgets.md) §5 + [`spec/host-decoupling.md`](spec/host-decoupling.md) §8 |
| **Tricky manifest fields** | [`spec/field-semantics.md`](spec/field-semantics.md) (**start here**) |
| Tool placement / visibility | [`spec/field-semantics.md`](spec/field-semantics.md) + [`host-decoupling.md`](spec/host-decoupling.md) §7 |
| Sessions / canvas open | [`spec/sessions.md`](spec/sessions.md) |
| `sys.*` in responses | [`spec/system-widgets.md`](spec/system-widgets.md) |
| Capability tree | [`spec/capability-tree.md`](spec/capability-tree.md) |
| Host events | [`spec/events.md`](spec/events.md) |
| Configuration UI | [`spec/configuration.md`](spec/configuration.md) |
| Host bindings | [`spec/host-injection.md`](spec/host-injection.md) |
| Before PR | [`spec/verify.md`](spec/verify.md) |
| Full index | [`spec/INDEX.md`](spec/INDEX.md) |

---

## What this is NOT

Host `/api/skills` is for **end-user** business playbooks at runtime — not for teaching coding agents the protocol. Use this developer pack instead.

---

## Bootstrap (Tier 0)

Paste from [`docs/tier0-bootstrap.prompt.md`](docs/tier0-bootstrap.prompt.md) into agent config. Keep that file in git.
