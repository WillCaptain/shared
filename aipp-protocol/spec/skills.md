# Skills — Progressive Disclosure Playbooks

> **Discovery:** [`AGENTS.md`](../AGENTS.md) → [`INDEX.md`](INDEX.md) → this file.  
> **Verify:** `assertValidSkillsApiStructure`, `assertValidSkillStructure` — [`verify.md`](verify.md).  
> **Tools (atomic):** [`tool-manifest.md`](tool-manifest.md) — single LLM call = Tool, not Skill.

---

## 1. Tool vs Skill

| | Tool | Skill |
|--|------|-------|
| LLM sees | Full function schema in tool list | Index: `name` + `description` only |
| Execution | One call → one response | LLM loads playbook, multi-step |
| Endpoint | `POST /api/tools/{name}` | `GET /api/skills` + `GET /api/skills/{name}/playbook` |
| Good for | `recipe_get`, `search`, `delete` | "Plan weekly menu", "onboard employee" |
| Bad for | Multi-step LLM orchestration in one tool name | Single atomic API wrapper |

**Rule:** If it needs a playbook with steps → Skill. If one call finishes → Tool.

**Criterion — orchestration side decides:** server-side orchestration (your code chains internal calls, opaque to the LLM) = **Tool**; LLM-side orchestration (LLM reads `status`, branches, asks the user mid-flow) = **Skill**. What the LLM sees is contract; what it doesn't see is implementation.

---

## 2. Dual-track structure

```
GET /api/skills          → light index (~200 bytes/entry)
GET /api/skills/{id}/playbook  → SKILL.md body (on demand)
GET /api/skills/{id}/files     → read-only package file tree (optional UI)
```

Empty `skills: []` is valid — skills are optional.

### 2.1 Mandatory serving rule — `AippSkillPackages`

Skills are **saved** as classpath packages (`resources/skills/{id}/SKILL.md`) and **served**
exclusively through `org.twelve.aipp.skill.AippSkillPackages`:

```java
@GetMapping("/skills")
public Map<String, Object> skills() throws Exception {
    return AippSkillPackages.buildSkillsResponse(getClass().getClassLoader(), APP_ID, VERSION, APP_ID);
}
// playbook → AippSkillPackages.readPlaybook(cl, id)   (null → 404)
// files    → AippSkillPackages.buildFilesResponse(cl, id)
```

- **Single source of truth:** every index field (`name`, `description`, `allowed-tools`,
  `level`, `owner_*`, `env`/`envs`, `catalog_manual`) comes from SKILL.md **frontmatter** —
  never hardcode index entries in Java. Duplicated metadata drifts; `buildSkillsResponse`
  already runs the description lint.
- **Apps with zero skills still use this** — the loader returns `skills: []` when no
  package exists, and a later SKILL.md drop-in works with no code change.
- Reference: world-entitir `SkillsController` (4 packages) and world-one
  `WorldoneSkillsController` (zero packages — same code).
- Host side: `SkillCatalog` pulls the index at app load and lazy-caches playbooks;
  the imported overlay (`~/.world-one/imported/skills/`) is parsed by the **same**
  `AippSkillPackages.buildIndexEntry` — there is exactly one frontmatter parser
  in the ecosystem.

---

## 3. Index entry (required fields)

```json
{
  "name": "plan_weekly_menu",
  "description": "Plan a 7-day meal schedule from dietary constraints and pantry. Use when the user asks to \"plan my week\", \"做一周菜谱\", or wants a full weekly schedule. Never creates new recipes — only schedules existing ones. Requires at least 5 recipes in the library.",
  "allowed_tools": ["recipe_list", "recipe_get", "pantry_query", "calendar_write"],
  "playbook_url": "/api/skills/plan_weekly_menu/playbook",
  "level": "app",
  "owner_app": "recipe-one"
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `name` | ✅ | snake_case; matches playbook path |
| `description` | ✅ | 40–1024 chars; **WHAT + WHEN** — only recall signal |
| `allowed_tools` | ✅ | Non-empty; every name must exist in some app's `/api/tools` |
| `playbook_url` | ✅ | Usually `/api/skills/{name}/playbook` |
| `level` | Recommended | `app` or `widget` |
| `owner_app` / `owner_widget` | Per level | `widget` level needs `owner_widget` |
| `catalog_manual` | Optional | Host capability browser exposure only |

### Description lint

- Must include WHEN clause: `use when`, `when the user`, `用于`, `当用户`, …
- Host may drop skills whose `allowed_tools` reference missing tools.

**Template:** `[WHAT]. Use when [triggers/scenarios]. [What NOT to do]. [Preconditions].`

---

## 4. SKILL.md playbook

`GET /api/skills/{name}/playbook` → `Content-Type: text/markdown;charset=UTF-8`

```markdown
---
name: plan_weekly_menu
description: Plan a 7-day meal schedule… Use when the user asks…
allowed-tools:
  - recipe_list
  - recipe_get
  - pantry_query
  - calendar_write
---

# Plan Weekly Menu

## Pre-conditions
- At least 5 recipes in library

## Procedure
### Step 1 — Gather constraints
…

### Step 2 — Call tools
1. `pantry_query()`
2. `recipe_list(...)`

## Don'ts
- Do not create recipes silently

## Status branches
- `not_found` → tell user, ask confirm before create
- `awaiting_confirmation` → stop until user acts
```

Frontmatter uses **`allowed-tools`** (hyphen). The loader (`AippSkillPackages`) tolerates `allowed_tools` (underscore) and normalizes both to `allowed_tools` in the JSON index — but write the hyphen form in SKILL.md.

Recommended sections: Pre-conditions, Parameters, Procedure (numbered steps), Don'ts, status branch table.

---

## 5. Skill-only manifest fields (on tool entries — legacy note)

Skills in the **index** are separate from **tool** entries. Tool entries in `/api/tools` must **not** include deprecated `prompt`, `tools[]`, or `resources` (rejected by `assertValidSkillStructure`).

Optional on **tool entries** in `/api/tools` (not on the skill playbook index):

| Field | Purpose |
|-------|---------|
| `lifecycle` | Host auto-schedule: `pre_turn` (before LLM) / `post_turn` (after turn) — see [`host-decoupling.md`](host-decoupling.md) §2 |
| `inject_context` | Host injects `_context` / `turn_messages` into the tool request |
| `memory_hints` | Aggregated into Memory Agent system prompt |
| `output_widget_rules` | Force canvas when response fields match |

Example: `memory_load` uses `lifecycle: "pre_turn"`; `memory_consolidate` uses `post_turn`. Playbook skills do **not** replace these — host scheduling stays on atomic tools.

---

## 6. `inject_context` & `memory_hints`

On skill or tool entry:

```json
"inject_context": { "request_context": true, "turn_messages": true },
"memory_hints": "Track user dietary preferences as PROCEDURAL memory."
```

| Field | Effect |
|-------|--------|
| `inject_context.request_context` | Host injects `_context` fields into skill execution |
| `inject_context.turn_messages` | Full turn message list (e.g. memory consolidation) |
| `memory_hints` | Aggregated into Memory Agent system prompt |

---

## 7. Anti-patterns

| Don't | Do instead |
|-------|------------|
| Keyword stuffing in description | One clear WHAT + WHEN paragraph |
| Empty `allowed_tools` | List every tool the playbook may call |
| "After calling me, call X" in tool description | Skill playbook or server-side orchestration |
| Put protocol docs in `/api/skills` | Use [`AGENTS.md`](../AGENTS.md) for coding agents |

---

## Related

- Sessions in playbook flows: [`sessions.md`](sessions.md)
- Tool manifest: [`tool-manifest.md`](tool-manifest.md)
