# Host Decoupling Fields (Manifest Self-Description)

> **Discovery:** [`AGENTS.md`](../AGENTS.md) → [`INDEX.md`](INDEX.md) → this file.  
> **Tricky fields:** read [`field-semantics.md`](field-semantics.md) first (placement vs `mutates_display` vs `refresh_tool`).  
> **Principle:** Host must not hardcode your app. All behavior via manifest fields — Host only understands these field semantics, never your app's names.

---

## 1. `output_widget_rules` (on tool/skill entry)

```json
"output_widget_rules": {
  "force_canvas_when": ["graph", "session_id"],
  "default_widget": "recipe-board"
}
```

When response JSON contains **all** `force_canvas_when` fields non-empty → Host forces canvas even if `html_widget` is present.

Without this → default priority in [`tool-responses.md`](tool-responses.md).

Verify: `assertValidOutputWidgetRules`.

---

## 2. `lifecycle` — when Host calls your tool (not the LLM)

Declare on a **tool entry** in `GET /api/tools` (not on the skill playbook index).

Host uses this so it never hardcodes app-specific names like `memory_load` / `memory_consolidate`. Your manifest says *when* to auto-invoke; Host schedules generically.

### Values

| Value | When Host runs it | In LLM tool list? |
|-------|-------------------|-------------------|
| `on_demand` (default) | Only when LLM, UI, or user invokes | Yes (if `visibility` includes `llm`) |
| `pre_turn` | **Before** each user message, before the LLM turn | **No** — host-internal |
| `post_turn` | **After** each completed user↔assistant turn, async (fire-and-forget) | **No** — host-internal |

### Turn timeline (example: memory-one)

```
User sends message
  │
  ├─ pre_turn   → Host POST /api/tools/memory_load
  │               (inject_context.request_context; returns memory_context)
  │               Host injects result as hidden background for the LLM
  │
  ├─ on_demand  → LLM may call memory_view, memory_query, world_* …
  │
  ├─ User sees assistant reply
  │
  └─ post_turn  → Host POST /api/tools/memory_consolidate (async)
                  (inject_context.turn_messages; persists the turn)
```

### Example tool entries (memory-one)

**`pre_turn` — preload context before the LLM thinks:**

```json
{
  "name": "memory_load",
  "description": "Load memory relevant to the current conversation (Host calls before each turn; not for LLM).",
  "parameters": {
    "type": "object",
    "properties": {
      "user_message": { "type": "string", "description": "Current user message for retrieval" }
    },
    "required": []
  },
  "canvas": { "triggers": false },
  "lifecycle": "pre_turn",
  "inject_context": { "request_context": true },
  "visibility": ["host"]
}
```

**`post_turn` — persist after the turn ends:**

```json
{
  "name": "memory_consolidate",
  "description": "Consolidate this turn into long-term memory (Host calls after each turn; not for LLM).",
  "parameters": { "type": "object", "properties": {}, "required": [] },
  "canvas": { "triggers": false },
  "lifecycle": "post_turn",
  "inject_context": { "turn_messages": true },
  "visibility": ["host"]
}
```

**`on_demand` — normal LLM-visible tool (default; `lifecycle` may be omitted):**

```json
{
  "name": "memory_view",
  "description": "Open the memory manager panel. Use when the user explicitly asks to manage memories…",
  "lifecycle": "on_demand",
  "visibility": ["llm", "ui"],
  "canvas": { "triggers": true, "widget_type": "memory-manager" }
}
```

### Rules for authors

1. **`pre_turn` / `post_turn` tools must not rely on the LLM** — put orchestration in the tool handler or `inject_context`; do not use deprecated `prompt` / `tools[]` on tool entries.
2. Set **`visibility: ["host"]`** (or let your app's enrich helper derive it from `lifecycle`).
3. Pair with **`inject_context`** when Host must pass session metadata or the full turn:
   - `pre_turn` → often `inject_context.request_context`
   - `post_turn` → often `inject_context.turn_messages`
4. **`canvas.triggers` is usually `false`** for host-scheduled tools (they return data, not UI).

### Legacy fields (removed from manifests)

| Old field | Use instead |
|-----------|-------------|
| `auto_pre_turn: true` | `lifecycle: "pre_turn"` |
| `background: true` | `lifecycle: "post_turn"` |

**New AIPP apps must use `lifecycle` only.** Host no longer reads `auto_pre_turn` / `background` on tool entries.

Verify: `assertValidLifecycle` on each tool entry that declares `lifecycle`.

---

## 3. `runtime_event_callbacks` (on `/api/tools` root)

Standard shape — **array**:

```json
"runtime_event_callbacks": [
  { "events": ["decision_result"], "path": "/api/recipes/{recipeId}/decision-result" },
  { "events": ["action_resume"], "path": "/api/recipes/{recipeId}/resume-action" }
]
```

Host POSTs to matching `path` (template vars from context). Host also accepts a legacy single-object `runtime_event_callback` at the root or on a skill entry.

Verify: `assertValidRuntimeEventCallbacks(toolsRoot)` for the root-level declaration (covers both array and single-object forms); `assertValidRuntimeEventCallback(skillEntry)` for a per-skill callback.

---

## 4. `event_subscriptions`

On `/api/tools` root or per tool:

```json
"event_subscriptions": ["workspace.changed", "user.login", "session.closed"]
```

Requires `POST /api/events` on your app — see [`events.md`](events.md).

Verify: `assertValidEventSubscriptions`.

---

## 5. `display_label_zh` (tool entry UI label)

```json
"display_label_zh": "新建菜谱"
```

`display_name` is **deprecated** on tool entries — Host still reads it as fallback; new apps should use `display_label_zh` only.

Host aggregates `GET /api/tool-labels` — you supply labels, Host does not maintain per-app dictionaries.

---

## 6. `prompt_contributions` (on `/api/tools` root)

Root-level `system_prompt` is **deprecated** — express the same content as `prompt_contributions[layer=ambient_prompt]` (Host still reads `system_prompt` for old apps).

```json
"prompt_contributions": [
  {
    "layer": "ambient_prompt",
    "priority": 100,
    "content": "【菜谱域】用户提到菜/食材时用 recipe_* 工具…"
  }
]
```

| layer | When injected |
|-------|----------------|
| `ambient_prompt` | Always injected (forest routing + active-app context) |
| `entry_prompt` | After the app/route is entered (takeover playbook) |

Same layer: higher `priority` first. Within the same `priority`, stable app-registration order; `priority` defaults to 0. Optional `id` (unique per app) helps debugging. Dynamic override: `entry_prompt_hit` on tool response — [`tool-responses.md`](tool-responses.md) §5.1.

### 6.1 Budget & ordering for `ambient_prompt`

`ambient_prompt` is injected into **every** session, so each contribution is a
permanent tax on the context window of all apps — it compounds as more shared
capabilities (memory-one, outline-one, …) are installed. Keep it disciplined:

- **Length budget.** A *reactive* "use this when…" `ambient_prompt` SHOULD be
  ≤ ~600 characters (a few lines): state *when* to use the capability and point at
  the skill — do **not** inline the playbook. *Behavioral* policies that change
  every-turn behavior (e.g. memory transparency rules) may be longer. As a hard
  anti-bloat gate, any single `ambient_prompt` contribution MUST be
  ≤ **2000 characters** (`AippAppSpec.assertValidPromptContributions`,
  `MAX_AMBIENT_PROMPT_CHARS`); beyond that, move depth into the SKILL.md
  (progressive disclosure) and on-demand reference tools (e.g. `outline_grammar`).
  (See [`capability-providers.md`](capability-providers.md).)
- **One per capability.** Ship a single, `id`-tagged ambient contribution per app
  (e.g. `memory-intent-policy`, `outline-intent-policy`), not several.
- **Priority bands.** Reserve high `priority` (≈100) for *behavioral* policies
  that change how the agent acts every turn (e.g. memory transparency). Use lower
  `priority` (≈10–50) for *reactive* "use this when…" pointers (e.g. a language
  service that only matters on demand) so behavioral rules sort first.
- **Reactive ≠ proactive.** If the capability is on-demand (outline), do **not**
  also add a `pre_turn` lifecycle tool — the ambient pointer is enough. Reserve
  `pre_turn`/`post_turn` for capabilities that are genuinely relevant every turn
  (memory). See [`capability-providers.md`](capability-providers.md) §4.

---

## 7. Tool placement (`visibility` + optional fields)

**v3+ (preferred)** — flat fields on each `GET /api/tools` entry:

```json
{
  "name": "world_modify_decision",
  "visibility": ["llm"],
  "owner_widget": "entity-graph"
}
```

```json
{
  "name": "decision_list_view",
  "visibility": ["llm", "ui"],
  "router_shortcut": true
}
```

| Field | Meaning |
|-------|---------|
| `visibility` | **Who** may call: `llm` (agent loop), `ui` (widget `hostApi`), `host` (Host scheduler). **Omitted → Host treats as `["llm"]`** (`ToolPlacement.visibilityContains`) |
| `owner_widget` (optional) | **Widget-bound** tool; LLM tools appear only when that canvas is active (main chat excludes them) |
| `router_shortcut` (optional) | Router may one-hop in root main session |
| `mutates_display` (optional) | Tool may stale widget UI; Host may auto-call widget `refresh_tool` after LLM turn (see §8) |

Omit `owner_widget` → app-wide tool (main chat + canvas base list).

**UI-only widget tools:** `visibility: ["ui"]` + `owner_widget` — Property Panel / buttons call via `hostApi`, not LLM.

**Read vs write on same widget:** `memory_query` may be widget-bound without `mutates_display`; only mutating writes set `mutates_display: true`.

**Legacy `scope` object** (`level`, `owner_app`, `visible_when`) is removed (2026-06): the Host no longer reads it. Declare placement with the flat fields above.

**Widget manifest `scope`** (`tools_allow` / `tools_deny`) is unrelated — runtime filter while a canvas is open.

`catalog_manual: true` is an **exposure marker only** (capability browser); it does not affect LLM visibility. Host filters per group independently: `catalog_manual` on a tool affects the tool group; the skill group reads the same-named field on the skill index — no cross-group dedup. Convention: one-click UI / manual-invoke entries mark the **tool**; multi-step playbooks mark the **skill index** — never both for the same user entry point.

---

## 8. Widget display refresh (`refresh_tool` + `mutates_display`)

**Problem:** After LLM or UI calls a write tool, the canvas may show stale data unless something reloads the view.

**v2.7 split:**

| Declare on | Field | Meaning |
|------------|-------|---------|
| Widget manifest (`GET /api/widgets`) | `refresh_tool` | Tool name Host/LLM should call to reload widget data (e.g. `memory_view`, `world_design`) |
| Tool manifest (`GET /api/tools`) | `mutates_display: true` | This tool may invalidate the widget display (pair with same `owner_widget`) |

**Views hint placeholder:**

```json
"views": [
  { "id": "ALL", "label": "All", "llm_hint": "After edits call {refresh_tool}." }
],
"refresh_tool": "recipe_view"
```

Host replaces `{refresh_tool}` (and legacy `{refresh_skill}`) in `llm_hint` at runtime.

**Host behavior (generic, no app hardcoding):**

1. Inject view `llm_hint` + reminder listing tools with `mutates_display` for the active widget.
2. After each LLM turn: if any called tool has `mutates_display` for the active widget **and** `refresh_tool` was not called → Host POSTs `refresh_tool` once (fallback).
3. Widget frontend may also call `hostApi.proxyTool(refresh_tool, …)` directly.

**Author checklist:**

- [ ] Widget declares `refresh_tool` (non-empty tool name)
- [ ] Every write tool that changes canvas data: `owner_widget` + `mutates_display: true`
- [ ] Read/query tools: omit `mutates_display`
- [ ] `llm_hint` uses `{refresh_tool}`, not hardcoded tool names

**Removed from widget manifest (legacy one release):**

| Old | Use instead |
|-----|-------------|
| `refresh_skill` | `refresh_tool` |
| `mutating_tools: [...]` | `mutates_display` on each tool in `/api/tools` |

Verify: `assertWidgetDeclaresRefreshTool` (v2.8: legacy `refresh_skill` / `mutating_tools` removed and rejected by `assertWidgetUsesCompressedFields`).

Details: [`widgets.md`](widgets.md) §5.

---

## 9. Configuration vs bindings

| Store | For |
|-------|-----|
| `GET/PUT /api/configuration` | User-editable app settings (`configuration.ui` on `/api/app`) |
| `PUT /api/host/bindings` | Host-injected `env`, `host_base_url`, callbacks |

**Never** put `env` or Host URLs in `configuration.values`.

---

## Related

- [`tool-manifest.md`](tool-manifest.md) — tool entry structure
- [`widgets.md`](widgets.md) — views, `refresh_tool`, ESM
- [`host-injection.md`](host-injection.md)
- [`configuration.md`](configuration.md)
- [`display-titles.md`](display-titles.md) — session / event / widget 标题命名
