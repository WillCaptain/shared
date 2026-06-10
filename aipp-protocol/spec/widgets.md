# Widget Manifest & Frontend (ESM)

> **Discovery:** [`AGENTS.md`](../AGENTS.md) → [`INDEX.md`](INDEX.md) → this file.  
> **Tricky fields:** [`field-semantics.md`](field-semantics.md) — placement vs `mutates_display` vs `refresh_tool`.  
> **Verify:** [`verify.md`](verify.md) — `AippWidgetSpec`, `assertValidWidgetsApiStructure`.  
> **System widgets:** [`system-widgets.md`](system-widgets.md) — do **not** register `sys.*` here.

---

## 1. Manifest required fields

| Field | Required | Notes |
|-------|----------|-------|
| `type` | ✅ | Globally unique, **not** `sys.*` prefix |
| `app_id` | ✅ | Same as `GET /api/app.app_id` |
| `is_main` | ✅ | Exactly **one** widget per app with `true` |
| `display_mode` | ✅ | `canvas` \| `chat` \| `pop` |
| `source` | ✅ | `external` (recommended) or `builtin` |
| `render` | ✅ (non-system) | App-owned ESM renderer |
| `description` | ✅ | Human-readable |

### `render` (Plan D)

```json
"render": {
  "kind": "esm",
  "url": "/widgets/recipe-board/recipe-board.js"
}
```

`url` may be absolute or app-relative; Host resolves app-relative via registered `base_url`.

---

## 2. ESM module contract

Export:

```js
export function mount(targetEl, hostApi, data) {}
export function unmount() {}
```

Optional read-only preview (capability catalog thumbnails):

```js
export function preview(targetEl, hostApi, context) {}
```

Preview `context`: `{ widget_type, app_id, display_mode, title?, description? }`.  
When `hostApi.preview === true`, `callTool` / `proxyTool` are no-op — no session side effects.

---

## 3. `hostApi` (only channel to Host)

| API | Use |
|-----|-----|
| `callTool(name, args)` | User action → new Host turn / tool call |
| `proxyTool(name, args)` | In-widget refresh without new chat message |
| `appProxyUrl(path)` | App-relative HTTP via Host proxy |
| `sessionId`, `workspace`, `appId`, `appBaseUrl` | Read-only context |

Do **not** use `parent.postMessage` as primary protocol or read Host globals.

Host passes tool results into widget via updated `html_widget.data` or `canvas` payload — not by widget reaching into Host internals.

---

## 4. `supports` — disable & theme

```json
"supports": {
  "disable": true,
  "theme": ["background", "surface", "text", "textDim", "border",
            "accent", "font", "fontSize", "radius", "language"]
}
```

- `disable: true` → mutating tools must return `{"ok":false,"error":"widget_disabled"}` when disabled; read-only tools still work.
- `theme` → Host injects CSS variables `--aipp-bg`, `--aipp-surface`, … — use `var(--aipp-bg)` in widget CSS.

---

## 5. Views & display refresh (multi-tab / editable canvas)

### 5.1 View reporting

```json
"views": [
  {
    "id": "ALL",
    "label": "全部",
    "llm_hint": "User views all recipes. After edits call {refresh_tool}."
  }
],
"refresh_tool": "recipe_view"
```

| Field | Required | Notes |
|-------|----------|-------|
| `views[].id` | When using views | Stable id; frontend passes to `aippReportView(widgetType, viewId)` |
| `views[].label` | When using views | Human label (debug / logs) |
| `views[].llm_hint` | When using views | Injected when user is on this tab; use `{refresh_tool}` placeholder |
| `refresh_tool` | Recommended for editable widgets | Tool that reloads widget data (often same as `entry_tool`) |

Optional per-view `system_prompt` and `scope.tools_allow` / `tools_deny` further narrow LLM tools while that tab is active.

### 5.2 Side effects on tools (not on widget)

Declare **which tools stale the canvas** on `/api/tools`, not on the widget manifest:

```json
{
  "name": "recipe_update",
  "owner_widget": "recipe-board",
  "visibility": ["llm", "ui"],
  "mutates_display": true
}
```

| Tool kind | `mutates_display` |
|-----------|-------------------|
| Create / update / delete / link writes | `true` |
| Query / read / open panel | omit or `false` |

### 5.3 Host + frontend behavior

- Frontend calls `aippReportView(widgetType, viewId)` on tab change.
- Host injects matching `llm_hint` at highest prompt priority next turn.
- Host replaces `{refresh_tool}` (legacy `{refresh_skill}` still accepted one release).
- After LLM turn: if any `mutates_display` tool ran and LLM did not call `refresh_tool` → Host may POST `refresh_tool` once (fallback).
- Widget may refresh immediately via `hostApi.proxyTool(refresh_tool, args)` without a new chat message.

Full Host contract: [`host-decoupling.md`](host-decoupling.md) §8.

### 5.4 Author checklist

- [ ] `refresh_tool` set to a real tool in `/api/tools`
- [ ] Write tools: `owner_widget` + `mutates_display: true`
- [ ] Hints use `{refresh_tool}`, not hardcoded names
- [ ] Do **not** add `mutating_tools` on widget (deprecated)

---

## 6. Upload (optional)

Declare on manifest when widget accepts file drops:

```json
"upload": {
  "accept": [".pdf", ".txt"],
  "prompt": "User uploaded a file. Validate then call document_ingest when appropriate.",
  "tools": ["document_ingest"]
}
```

Verify with `AippWidgetSpec.assertWidgetSupportsUpload`, `assertUploadAccepts`, …

---

## 7. Recommended manifest extras

| Field | Purpose |
|-------|---------|
| `entry_tool` | Tool Host calls to open this widget (from Apps panel or canvas entry) |
| `widget_prompt` | LLM domain manual while widget is active |
| `welcome_message` | User-facing text when canvas session opens |
| `main_widget_type` on `/api/app` | Entry when no custom main UI — use `sys.app-info` |

---

## 8. Title resolution (widget ESM)

For chat/card titles, resolve:

```js
effectiveTitle =
  data.title ?? data.context_title ?? data.schema?.title ?? fallbackI18n
```

Host passes through payload; widget owns display. See README §6.7 for `session_summary` / `context_title` producers.

---

## 9. Full example

```json
{
  "type": "recipe-board",
  "app_id": "recipe-one",
  "is_main": true,
  "display_mode": "canvas",
  "source": "external",
  "render": { "kind": "esm", "url": "/widgets/recipe-board/recipe-board.js" },
  "description": "Recipe management panel",
  "supports": { "disable": true, "theme": ["background", "surface", "text", "accent"] },
  "views": [
    { "id": "ALL", "label": "All", "llm_hint": "Viewing all recipes. {refresh_tool} after edits." }
  ],
  "refresh_tool": "recipe_view"
}
```

Tools (on `/api/tools`) declare side effects:

```json
{ "name": "recipe_update", "owner_widget": "recipe-board", "visibility": ["llm", "ui"], "mutates_display": true }
```

---

## 10. Common mistakes

| Mistake | Fix |
|---------|-----|
| Register `sys.confirm` in `/api/widgets` | Return `sys.*` in **tool response** only |
| Zero or multiple `is_main: true` | Exactly one per app |
| Widget calls Host internals | Use `hostApi` only |
| `display_mode: pop` but return `html_widget` without pop registry | Set manifest `display_mode: pop` |
| List writes in widget `mutating_tools` | Set `mutates_display` on each write tool in `/api/tools` |
| Hardcode `memory_view` in `llm_hint` | Use `{refresh_tool}` + widget `refresh_tool` field |
| Canvas stale after LLM edit | Missing `mutates_display` on write tool or missing `refresh_tool` on widget |

---

## Related

- Opening widgets from tools: [`tool-responses.md`](tool-responses.md)
- Sessions when opening canvas: [`sessions.md`](sessions.md)
- README §5 (encyclopedia slice)
