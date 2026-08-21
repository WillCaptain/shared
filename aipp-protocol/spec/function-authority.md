# Function authority — gated tools and skills

> **Discovery:** [`INDEX.md`](INDEX.md) → this file.  
> **Verify:** `AippFunctionAuthoritySpec` — [`verify.md`](verify.md).  
> **Identity:** [`user-identity.md`](user-identity.md).  
> **Cross-app calls:** [`capability-providers.md`](capability-providers.md) — depend on the tool **name** `register_function`, never on `user-one` URLs.

---

## 1. Motivation

Some tools and skills change shared business data and must be assigned, not left open to every org member. Those capabilities are **function points**. They live in user management (12th-users via user-one). The Host enforces them.

| Rule | Detail |
|------|--------|
| Unregistered | Open. Existing tools keep working. |
| Registered | Explicit grant required. Role wildcard `*` does **not** match. |
| Function id | `{app_id}::{name}` — same as Host `CapabilityId`. |
| Cross-app | Call `register_function` by **name**. Do not hardcode `user-one` or 12th URLs. |

AIPP authors **MUST** register every tool or skill that needs authority management. The Host **MUST** deny ungranted registered functions.

---

## 2. Manifest flag

On a tool entry (`GET /api/tools`) or skill frontmatter that needs a grant:

```json
{
  "name": "world_list_view",
  "requires_authority": true
}
```

| Field | Type | Meaning |
|-------|------|---------|
| `requires_authority` | boolean | This capability must be in the function catalog. Absent = false. |

`requires_authority: true` does **not** itself grant or deny. It is the declaration that the AIPP will register (or that the Host should register on install) via `register_function`.

Main-widget `entry_tool` that gates editing the whole app SHOULD also set `gates_app: true` when registering (see §3). That is a catalog field, not a tool-manifest field.

---

## 3. Tool: `register_function`

| Field | Value |
|-------|--------|
| Name | `register_function` |
| Owner | `user-one` only |
| `visibility` | `["host", "llm"]` |
| `side_effect` | `idempotent` |
| Auth | Catalog write is idempotent and does not require a user grant. Host install MAY call it without Bearer. |

### Args

| Name | Type | Required | Notes |
|------|------|----------|-------|
| `app_id` | string | yes* | AIPP `app_id` (`world`, not the repo name) |
| `name` | string | yes* | Tool or skill name (`world_list_view`) |
| `function_id` | string | yes* | Alternative to `app_id`+`name`. Must be `{app_id}::{name}` |
| `kind` | string | no | `tool` (default) or `skill` |
| `title` | string | no | Display label |
| `gates_app` | boolean | no | If true, **every** tool/skill of `app_id` requires a grant to this function |

\* Either `function_id` **or** (`app_id` and `name`).

### Success

```json
{
  "ok": true,
  "function": {
    "function_id": "world::world_list_view",
    "app_id": "world",
    "name": "world_list_view",
    "kind": "tool",
    "title": "World list",
    "gates_app": true
  }
}
```

---

## 3.1 Tool: `find_user`

Look up a global user before assigning a grant. Do not special-case any display name in application code.

| Field | Value |
|-------|--------|
| Name | `find_user` |
| Owner | `user-one` |
| Args | `q` — username, display name, or email fragment |

## 4. Tool: `assign_function`

Assigns a registered function to a user (membership grant) in an organization.

| Field | Value |
|-------|--------|
| Name | `assign_function` |
| Owner | `user-one` |
| `visibility` | `["llm", "ui"]` |
| `side_effect` | `mutating` |
| Auth | Bearer. Caller MUST be `admin` in the target org. |

| Arg | Required | Notes |
|-----|----------|-------|
| `function_id` or `app_id`+`name` | yes | Catalog id |
| `username` or `user_id` | yes | Global user |
| `org_id` | no | Default `12th` |
| `data_scope` | no | `own` (default) / `self_node` / `node` / `org` / `none` |

---

## 5. Tool: `check_function`

| Field | Value |
|-------|--------|
| Name | `check_function` |
| Owner | `user-one` |
| `visibility` | `["host"]` |
| `side_effect` | `none` |
| Auth | Bearer of the user being checked |

```json
{ "ok": true, "allowed": false, "function_id": "world::world_list_view", "reason": "grant_required" }
```

`allowed` is true when the capability is unregistered, or the caller has an **explicit** grant (not `*`).

---

## 6. Host enforcement

On `POST /api/proxy/tools/{name}` (and equivalent agent-loop dispatch):

1. Resolve `{app_id}::{name}`.
2. Ask user-one `check_function` (Bearer + `_context.userId` / `orgId`).
3. If `allowed=false`, return **403** `{ "ok": false, "error": "function_denied", "function_id": "..." }`. Do not forward the call.

If a registered function has `gates_app=true`, every tool of that `app_id` is checked against that function.

Unreachable user-one while a function is known-registered → fail **closed**.

---

## 7. First instance

`world` (world-entitir) main widget `world-list` has `entry_tool=world_list_view`. That tool registers as `world::world_list_view` with `gates_app=true`. Grants are created with `assign_function` after looking up the user (`find_user`). Users without that grant are denied.

---

## 8. Verification

- `AippFunctionAuthoritySpec.assertValidFunctionId`
- `assertValidRegisterFunctionResponse`
- `assertUserOneOwnsRegisterFunction` on user-one `/api/tools`
- Host tests: unregistered tools still pass; registered + no grant → 403
