# Imported overlay — virtual capability forest

**Audience:** Ones (world-one) host developers.

**Discovery path:** [`INDEX.md`](INDEX.md) → this file.

---

## 1. Purpose

Host-managed **community** skills and proxy tools live outside any registered AIPP JAR.
They appear as a **virtual forest** `app_id: imported` — a peer of `world`, `memory-one`, `worldone-system`, etc.

- **Not** a remote HTTP app (no `AppRegistry` base URL).
- **On disk:** `~/.world-one/imported/`
- **Discovery:** searched **after** all registered AIPPs and `worldone-system`.

---

## 2. Forest shape

```
imported::root
├── skill-{id}          (leaf — default)
├── tool-{id}           (leaf — proxy meta only)
└── route-{group}/      (optional — only when grouping >1 capability)
    ├── skill-…
    └── tool-…
```

**No** redundant wrapper (`imported` inside `imported`). The app id **is** `imported`; root local id is `root`.

Routes are for real grouping (pack, category). Do not add 1:1 parent routes.

---

## 3. On-disk layout

```
~/.world-one/imported/
  manifest.json
  skills/{entry_id}/
    SKILL.md
    resources/…           # full package copy
  tools/{entry_id}/
    tool.json             # meta + proxy (no handler code)
```

### `manifest.json`

```json
{
  "version": 1,
  "entries": [
    {
      "id": "weekly-plan",
      "kind": "skill",
      "ref_name": "weekly_plan",
      "source": "file:/path/to/source",
      "imported_at": "2026-06-10T12:00:00Z",
      "route_id": "",
      "title": "Weekly plan",
      "description": "…"
    }
  ]
}
```

| Field | Notes |
|-------|--------|
| `id` | Folder name under `skills/` or `tools/` |
| `kind` | `skill` or `tool` |
| `ref_name` | Skill/tool name in `ref` (from frontmatter or tool.json) |
| `route_id` | Optional; shared non-blank value groups siblings under `route-{route_id}` |

---

## 4. Skills vs tools

| | Skills | Tools |
|--|--------|-------|
| Import | Copy **entire** package tree | Copy **meta** only (`tool.json`) |
| Execution | LLM + playbook → existing or proxy tools | Host forwards to `proxy.url` via `ImportedToolProxy` |
| Lint | `SKILL.md` frontmatter + `allowed_tools` | JSON schema + unique `name` |

---

## 5. Host API (Ones host)

| Endpoint | Action |
|----------|--------|
| `GET /api/imported` | Manifest summary |
| `POST /api/imported/skills` | `{ "source": "/abs/path" \| "file:…" \| "git:https://…", "id"?: "…", "route_id"?: "…" }` |
| `POST /api/imported/tools` | `{ "id"?: "…", "route_id"?: "…", "tool": { "name", "description", "visibility", "parameters", "proxy": { "url", "method"?: "POST" } } }` |
| `DELETE /api/imported/{kind}/{id}` | Remove entry + disk |
| `GET /api/imported/skills/{id}/playbook` | `SKILL.md` body |

After mutation: reload `SkillCatalog` + `ToolCatalog` imported indexes and rebuild `imported` capability tree.

### Tool proxy execution

- `POST /api/proxy/tools/{toolName}` and agent executor detect imported tools before `AppRegistry.findAppForTool`.
- Request body is forwarded as JSON to `tool.proxy.url` (default method `POST`).
- Imported tools appear in `ToolCatalog.toolsForLlm()` but lose name collisions to installed AIPP tools.

---

## 6. Discovery order

1. Registered AIPP forests (excluding virtual `imported`)
2. `worldone-system`
3. `imported` (last)

UI may list `imported` first for visibility; search order is independent.
Capability tree editor: select `imported::root` to open the **Imported** detail page (import skill/tool actions live there, not on the tree sidebar).

---

## 7. Capability tree integration

- `GET /api/capability-trees` includes `imported` tree.
- `GET /api/capability-trees/imported` returns virtual tree document.
- Node ids: `imported::{local_id}`.
- Leaf `ref`: `{ "type": "skill"|"tool", "name": "<ref_name>" }` with `owner_app: imported`.
