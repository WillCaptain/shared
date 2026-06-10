# AIPP Quickstart Checklist

> Actionable checklist for coding agents. Normative detail: README §1, §14, §17.  
> Tricky fields: [`spec/field-semantics.md`](../spec/field-semantics.md). Verify: [`spec/verify.md`](../spec/verify.md).

---

## 1. Scaffold

- [ ] Choose `app_id` (kebab-case, e.g. `recipe-one`)
- [ ] HTTP service with the 4 required endpoints (see below)
- [ ] Add `aipp-protocol` as **test** dependency (see [`spec/verify.md`](../spec/verify.md))
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
| Anti-patterns | README §16 |
