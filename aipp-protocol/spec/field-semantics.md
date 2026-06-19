# Protocol Field Semantics — Design Commentary for Coding Agents

> **Read this when** you touch `visibility`, `owner_widget`, `router_shortcut`, `mutates_display`, `refresh_tool`, or legacy `scope` / `mutating_tools`.  
> **Normative API:** [`host-decoupling.md`](host-decoupling.md) §7–§8, [`widgets.md`](widgets.md) §5.  
> **Reference implementation:** `org.twelve.aipp.tools.ToolPlacement` (aipp-protocol).

---

## 1. Four orthogonal concerns (do not conflate)

AIPP splits widget/tool metadata into **four independent axes**. Mixing them is the #1 agent mistake — in particular, "side effect" is overloaded: `mutates_display` is about the *UI*, `side_effect` is about the *world*. They are not the same flag.

| Axis | Question it answers | Where declared | Examples |
|------|---------------------|----------------|----------|
| **Placement** | *Who may call this tool, and in which Host surface?* | `/api/tools` | `visibility`, `owner_widget`, `router_shortcut` |
| **Display side effect** | *Does calling this tool make the open canvas show stale data?* | `/api/tools` | `mutates_display` |
| **Refresh contract** | *Which tool reloads widget data after a side effect?* | `/api/widgets` | `refresh_tool`, `views[].llm_hint` |
| **Retry safety** | *If this step fails mid-plan, can the orchestrator auto-retry it?* | `/api/tools` | `side_effect` (`none`/`idempotent`/`mutating`) |

```
placement (who/when visible)  ≠  mutates_display (stale UI)  ≠  refresh_tool (how to reload)  ≠  side_effect (safe to re-run)
```

A tool can be any combination: a `memory_update` write is `mutates_display: true` (UI stale) **and** `side_effect: idempotent` (safe to retry) — the two flags answer different questions and are set independently. Full `side_effect` semantics: [`tool-manifest.md`](tool-manifest.md) §3.1.

**Example (memory-manager):**

- `memory_query` — `owner_widget: memory-manager`, `visibility: [ui]` or `[llm,ui]`, **no** `mutates_display` (read-only)
- `memory_update` — same `owner_widget`, `mutates_display: true` (write)
- Widget — `refresh_tool: memory_view` (reload tool; often same as `entry_tool` but different jobs)

`owner_widget` says *where the tool belongs*; `mutates_display` says *whether Host should consider auto-refresh*.

---

## 2. `visibility` — who may invoke

**On:** each tool in `GET /api/tools`.

| Value | Caller | Typical use |
|-------|--------|-------------|
| `llm` | Agent loop (LLM tool call) | User-facing capabilities |
| `ui` | Widget `hostApi.callTool` / `proxyTool` | Property panel buttons, in-canvas edits without a chat turn |
| `host` | Host scheduler (`lifecycle: pre_turn` / `post_turn`) | `memory_load`, `memory_consolidate` |

**Rules:**

- Array may combine tokens: `["llm", "ui"]` = both LLM and widget UI.
- `lifecycle: pre_turn` / `post_turn` tools should use `visibility: ["host"]` (LLM must not see them).
- `visibility` does **not** imply widget binding — pair with `owner_widget` when needed.

---

## 3. `owner_widget` — widget-bound tools

**On:** tool entries that belong to a specific widget canvas.

| Effect | Behavior |
|--------|----------|
| Main chat LLM catalog | **Excluded** — `ToolCatalog.toolsForLlm()` skips `isWidgetLlmTool` |
| Canvas active for that widget | Merged into LLM tool list (stripped placement metadata via `stripPlacementForLlm`) |
| UI tools | `visibility: ["ui"]` + `owner_widget` = internal widget buttons only |

**When to set:**

- Tool only makes sense while `entity-graph` / `memory-manager` is open.
- Tool parameters assume canvas workspace (`session_id` from Host).

**When NOT to set:**

- App-wide tools (`world_design`, `recipe_list`) used from main chat or router shortcuts.
- `refresh_tool` itself is usually app-wide or canvas-open tool — check product; it does not need `mutates_display`.

**Not the same as** widget manifest `scope.tools_allow` / `tools_deny` (per-view runtime filter while canvas is open).

---

## 4. `router_shortcut` — main-session one-hop

**On:** app-wide tools the Router may call directly from root main chat without loading a skill playbook first.

| Removed legacy | v3 |
|--------|-----|
| `scope.level: "universal"` (no longer read) | `router_shortcut: true` |

**When to set:**

- High-frequency entry tools: `decision_list_view`, `recipe_list`, `world_list_view`.

**When NOT to set:**

- Widget-bound write tools (`owner_widget` + `mutates_display`).
- Host-scheduled tools (`visibility: ["host"]`).

**Dedup:** If two LLM tools share a name, `ToolPlacement.llmDedupRank` prefers app-wide > widget-bound; router shortcuts rank high among app-wide tools.

---

## 5. `mutates_display` — stale canvas after this call

**On:** write tools in `GET /api/tools` that change data the open widget renders.

| Set `true` | Omit or `false` |
|------------|-----------------|
| create / update / delete / link / promote | query / get / list / open-panel |
| Same `owner_widget` as the canvas | Read-only or opens UI only |

**Host behavior after an LLM turn (canvas active):**

1. Collect tools called this turn.
2. If any called tool has `mutates_display: true` **and** `owner_widget` matches active canvas → candidate for refresh.
3. If `refresh_tool` was **not** already called → Host POSTs `refresh_tool` once (fallback).
4. LLM hints (from `buildUiHints`) remind model to call `refresh_tool` first (primary path).

**Author rule:** declare on the **tool**, not a list on the widget. Widget `mutating_tools[]` is removed (v2.8): the validator rejects it and the Host ignores it with a warning.

**Pairing check:**

```json
{ "name": "memory_update", "owner_widget": "memory-manager", "mutates_display": true }
```

`mutates_display` without matching `owner_widget` on a widget-only canvas will not trigger widget-scoped refresh logic correctly.

---

## 6. `refresh_tool` — widget reload contract

**On:** `GET /api/widgets` entry for editable / multi-tab canvases.

| Field | Meaning |
|-------|---------|
| `refresh_tool` | Tool name Host/LLM should call to reload widget data (e.g. `memory_view`, `world_design`) |
| `views[].llm_hint` | Use `{refresh_tool}` placeholder — Host substitutes at runtime |

**Jobs compared:**

| Field | Job |
|-------|-----|
| `entry_tool` | Open / enter this widget (Apps panel, canvas entry) |
| `refresh_tool` | Re-fetch data for widget already open (after edits) |

Often the same tool name (`memory_view` does both) but **semantically different** — do not drop `refresh_tool` because `entry_tool` exists.

**Frontend:** may call `hostApi.proxyTool(refresh_tool, args)` immediately after a local edit (no chat message).

**Removed (v2.8):** `refresh_skill` → rename to `refresh_tool`; `ToolPlacement.refreshToolFromWidget` reads `refresh_tool` only and the validator rejects `refresh_skill`.

---

## 7. Legacy `scope` object (ingest only)

Nested `scope` on tools is **removed** (2026-06): the Host no longer reads it — a nested
`scope` is inert on ingest and stripped before any LLM sees the tool. Migration map for
old definitions:

| Old `scope` | Declare instead |
|----------------|---------|
| `level: "universal"` | `router_shortcut: true` |
| `level: "widget"` + `owner_widget` | top-level `owner_widget` |
| `visible_when: "canvas_open"` | implied by `owner_widget` + canvas merge (not a field) |
| `visible_when: "always"` + `visibility: ["ui"]` | UI-only widget tool |
| `level: "app"` / `owner_app` | nothing — app-wide is the default |

Widget-level `views[].scope.tools_allow` is **unrelated** — runtime LLM filter per tab.

---

## 8. Author decision tree

```
Adding a tool to /api/tools
│
├─ Host calls it every turn automatically?
│   └─ YES → lifecycle pre_turn/post_turn, visibility ["host"], no router_shortcut
│
├─ Only for widget UI buttons?
│   └─ YES → visibility ["ui"], owner_widget set, usually no mutates_display unless write
│
├─ LLM uses it only when canvas X is open?
│   └─ YES → visibility includes "llm", owner_widget: X
│       └─ Changes data shown on canvas?
│           └─ YES → mutates_display: true
│
├─ LLM uses it from main chat (list/open entry)?
│   └─ YES → no owner_widget; consider router_shortcut: true
│
└─ Editable widget manifest?
    ├─ refresh_tool → reload tool name
    ├─ views[].llm_hint → use {refresh_tool}
    └─ do NOT list mutating_tools on widget
```

---

## 9. Common agent mistakes

| Mistake | Why wrong | Fix |
|---------|-----------|-----|
| Put `mutating_tools` on widget | Removed in v2.8 — validator rejects it | `mutates_display` on each write tool |
| Set `mutates_display` on `memory_query` | Reads do not stale display | Omit flag |
| Omit `owner_widget` on canvas write tools | Tool appears in main chat; wrong context | Add `owner_widget` |
| Hardcode `memory_view` in `llm_hint` | Renaming breaks hints | `{refresh_tool}` |
| Use `refresh_skill` | Removed in v2.8 — validator rejects it | `refresh_tool` |
| Nest `scope.level: widget` on tools | Removed shape — Host ignores it | `owner_widget` + `visibility` |
| Confuse `entry_tool` with `refresh_tool` | Open vs reload are different contracts | Declare both when canvas is editable |
| Set `router_shortcut` on widget writes | Pollutes main router | Only entry/list tools |

---

## 10. Host code map (for agents patching world-one)

| Concern | Class / method |
|---------|----------------|
| Normalize flat placement fields | `ToolPlacement.normalize` |
| Main chat LLM catalog | `ToolCatalog.toolsForLlm` — excludes `isWidgetLlmTool` |
| Canvas LLM merge | `AppRegistry.getCanvasTools` — `stripPlacementForLlm` |
| Index `refresh_tool` | `AppRegistry.indexWidgetViewFields` |
| Detect stale display | `AppRegistry.isWidgetMutatingTool` — catalog `mutates_display` first |
| LLM hints | `AppRegistry.buildUiHints` |
| Auto refresh fallback | `GenericAgentLoop.autoRefreshIfNeeded` |

---

## Related

- [`host-decoupling.md`](host-decoupling.md) §7–§8
- [`widgets.md`](widgets.md) §5
- [`verify.md`](verify.md) § Protocol compression (2.4–2.7)
- Java: `ToolPlacement.java`, `AippWidgetSpec.assertWidgetDeclaresRefreshTool`
