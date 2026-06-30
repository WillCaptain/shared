# AIPP Quickstart Checklist

> Actionable checklist for coding agents. Normative detail: [`spec/app-manifest.md`](../spec/app-manifest.md), [`spec/tool-manifest.md`](../spec/tool-manifest.md), [`spec/verify.md`](../spec/verify.md) § Rules quick table. Minimal copyable template: appendix below.  
> Tricky fields: [`spec/field-semantics.md`](../spec/field-semantics.md). Verify: [`spec/verify.md`](../spec/verify.md).

---

## 1. Scaffold

- [ ] Choose `app_id` (kebab-case, e.g. `recipe-one`)
- [ ] HTTP service with the 4 required endpoints (see below)
- [ ] Add `aipp-protocol` as **test** dependency (see [`spec/verify.md`](../spec/verify.md))
- [ ] Add `aipp-protocol-spring` + `aipp.host.base-url` / `aipp.self-base-url` — [`spec/host-lifecycle.md`](../spec/host-lifecycle.md) (do **not** copy a per-app `HostRegistrar`)
- [ ] (Recommended) `PUT /api/host/bindings` listener — [`spec/host-injection.md`](../spec/host-injection.md)

---

## 2. Required endpoints

| Endpoint | Check |
|----------|--------|
| `GET /api/app` | `app_id`, `app_name`, `app_icon`, `app_description`, `app_color`, `is_active`, `version` |
| `GET /api/tools` | Top-level `app`, `version`, `tools[]`; each tool: `name`, `description`, `parameters`, `canvas`, `visibility` (+ optional `owner_widget` / `router_shortcut` / `mutates_display`) |
| `GET /api/widgets` | Top-level `app`, `version`, `widgets[]`; **exactly one** `is_main: true` |
| `POST /api/tools/{name}` | JSON body per tool `parameters`; returns tool result (optional UI envelope) |

Optional:

| Endpoint | When |
|----------|------|
| `GET /api/skills` + playbook routes | Multi-step flows |
| `GET/PUT /api/configuration` | `configuration.ui` present on `/api/app` |
| `PUT /api/host/bindings` | Host injects env / callbacks |
| `POST /api/events` | `event_subscriptions` declared |

---

## 3. First tool

- [ ] `name` in snake_case
- [ ] `parameters.type` = `"object"`
- [ ] `canvas.triggers` boolean present
- [ ] `visibility` array present (`llm` / `ui` / `host`)
- [ ] No nested `scope` on new apps (use flat placement — [`spec/host-decoupling.md`](../spec/host-decoupling.md) §7)
- [ ] No `prompt`, `tools[]`, or `resources` on the tool entry
- [ ] `POST /api/tools/{name}` returns `{ "ok": true, ... }` (plus optional `html_widget` / `canvas` / `pop_widget`)

---

## 4. First widget

- [ ] `type` is **not** `sys.*`
- [ ] `app_id` matches `/api/app`
- [ ] `render` URL or inline spec present (non-system widgets)
- [ ] Mark exactly one widget `is_main: true`
- [ ] Set `display_mode` (`canvas` | `chat` | `pop`) consistently with how tools open it
- [ ] Editable canvas: declare `refresh_tool` on widget; `mutates_display: true` on each write tool (see [`spec/widgets.md`](../spec/widgets.md) §5)

---

## 5. Optional skill

- [ ] Index entry: `name`, `description` (40–1024 chars, includes **when** to use), `allowed_tools` (non-empty), `playbook_url`
- [ ] Every `allowed_tools` name exists in `/api/tools`
- [ ] Playbook is markdown with frontmatter `name`, `description`, `allowed-tools`

---

## 6. Capability tree (on Host)

- [ ] Executable leaves: `kind: tool` or `skill`, `ref.name` = registered tool/skill name
- [ ] Widget types mirrored under `widgets/` folder for **catalog** — Router does not execute `kind: widget`
- [ ] Do **not** add `sys.selection` as a routable leaf — Host emits it when needed

Details: [`spec/capability-tree.md`](../spec/capability-tree.md)

---

## 7. Compliance gate (before done)

- [ ] JSON fixtures pass `AippAppSpec` structure asserts
- [ ] `assertAppIdConsistency`, `assertExactlyOneMainWidget`
- [ ] Widget/tool responses pass relevant asserts
- [ ] Run `mvn test` in `aipp-protocol` if you changed spec validators

Full commands: [`spec/verify.md`](../spec/verify.md)

---

## 8. Register on Host

Follow [`spec/host-registration.md`](../spec/host-registration.md):

- [ ] `POST {host}/api/registry/install` with `app_id` + `base_url`
- [ ] `GET {host}/api/registry` lists your app
- [ ] `GET {host}/api/capability-trees/{app_id}` (if used)
- [ ] Smoke: `POST {host}/api/chat` triggers at least one tool

---

## Next reads

| Need | Doc |
|------|-----|
| Tool responses | [`spec/tool-responses.md`](../spec/tool-responses.md) |
| Widget ESM | [`spec/widgets.md`](../spec/widgets.md) |
| Skills | [`spec/skills.md`](../spec/skills.md) |
| `sys.*` | [`spec/system-widgets.md`](../spec/system-widgets.md) |
| Anti-patterns | [`spec/verify.md`](../spec/verify.md) § Anti-patterns |

---

## Appendix — 完整最小 AIPP 模板（可直接抄）

把下面 4 个端点实现完，注册到 Host（[`spec/host-registration.md`](../spec/host-registration.md)），就是一个合规 AIPP。

<details>
<summary>展开示例</summary>

```http
GET /api/app
→ { "app_id":"recipe-one", "app_name":"菜谱", "app_icon":"<svg>...</svg>",
    "app_description":"菜谱管理", "app_color":"#ff8a65",
    "is_active":true, "version":"1.0" }

GET /api/tools
→ {
    "app":"recipe-one", "version":"1.0",
    "prompt_contributions":[{
      "layer":"ambient_prompt", "priority":100,
      "content":"用户提到「菜/菜谱/食材」走 recipe_* 工具。"
    }],
    "tools":[{
      "name":"recipe_list",
      "description":"列出菜谱（可按食材/分类筛选）。返回 html_widget 卡片。",
      "parameters":{ "type":"object","properties":{"query":{"type":"string"}},"required":[] },
      "canvas":{"triggers":false},
      "visibility":["llm","ui"],
      "router_shortcut":true,
      "display_label_zh":"菜谱列表"
    }]
  }

GET /api/skills
→ {
    "app":"recipe-one", "version":"1.0",
    "skills":[]   // 没有多步流程时合规，可省略本端点
  }

GET /api/widgets
→ {
    "app":"recipe-one", "version":"1.0",
    "widgets":[{
      "type":"recipe-board",
      "app_id":"recipe-one",
      "is_main":true,
      "display_mode":"canvas",
      "render":{"kind":"esm","url":"/widgets/recipe-board/recipe-board.js"},
      "description":"菜谱看板",
      "refresh_tool":"recipe_view",
      "views":[{"id":"ALL","label":"全部","llm_hint":"查看全部菜谱；修改后调用 {refresh_tool}。"}]
    }]
  }
```

Write tools also declare `mutates_display: true` on `/api/tools` (with matching `owner_widget`).

```http
POST /api/tools/recipe_list
← { "args":{"query":"番茄"}, "_context":{...} }
→ { "ok":true, "html_widget":{ "widget_type":"recipe-list","title":"菜谱","data":{...} } }
```

</details>
