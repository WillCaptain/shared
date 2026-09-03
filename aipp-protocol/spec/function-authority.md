# Function authority — gated tools and skills

> **Discovery:** [`INDEX.md`](INDEX.md) → this file.  
> **Contract:** `AippFunctionAuthoritySpec` constants — [`verify.md`](verify.md).
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

| `gates_app` | boolean | Optional. `true` means a grant on **this** function is required for **every** tool and skill of the app. Absent = false. |

`gates_app` must be **declared**, never inferred. The Host used to derive it from "is this tool the main widget's `entry_tool`", which turned a single `requires_authority: true` into an app-wide lock — every unregistered capability of that AIPP became gated, contradicting §2's rule that unregistered stays open. Set it only when you really mean "no grant, no app".

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

### 5.1 Tool: `check_functions` (batch)

Same owner, visibility and auth. No per-capability argument — it returns the caller's decision over the **whole catalog** in one call, and is what the Host actually uses (see §6.1). `check_function` remains for one-off inspection.

| Field | Value |
|-------|--------|
| Name | `check_functions` |
| REST | `GET /api/functions/effective` |
| Args | `org_id` (optional) |

---

## 6. Host enforcement

Enforcement has **two layers over one snapshot**. Both read the same answer; neither replaces the other.

### 6.1 Turn snapshot

Once per turn (and once per non-turn HTTP request), the Host asks user-one `check_functions` → `GET /api/functions/effective`. The response is **every registered function point with this caller's decision already resolved**:

```json
{ "ok": true, "user_id": "…", "functions": [
  { "function_id": "world::world_list_view", "app_id": "world", "name": "world_list_view",
    "kind": "tool", "gates_app": true, "allowed": false }
]}
```

Absent from the list = unregistered = open. The Host **does not evaluate grants** — role inheritance, membership and the `*`-does-not-match rule stay inside 12th-users. The Host only applies:

```
applicable = [appId::name if present] + [gates_app functions of appId]
allowed    = applicable.isEmpty() || applicable.any(allowed)
```

Cache per bearer, TTL 5s. Never call `check_function` per capability inside a loop.

### 6.2 Context filter (what the caller can see)

Before a list reaches a model or a screen, drop what the snapshot denies:

| Surface | Filtered |
|---|---|
| LLM tool schema / promoted router catalog | per tool |
| Skill catalog, router skill list | per skill |
| Capability tree, capability search candidates | per app (`gates_app`) and per capability |
| Apps panel, `GET /api/apps` | per app |
| Aggregated ambient / entry system prompt | per app |

Two things must **not** be filtered:

- The raw catalogs that **generate persisted structures** (capability-tree autogeneration reads them) — a stored tree must not encode one caller's grants.
- Any read that an editor **writes back whole**. Prune the serialized copy on browse/render paths only; if the tree editor loaded a pruned document and saved it, the denied nodes would be deleted for everyone.

### 6.3 Dispatch gate (what the caller can run)

A list is not a security boundary. Widget `callTool` → `/api/proxy/tools/{name}`, the task gateway, DAG nodes and event callbacks never build one, and a model can replay a tool name from earlier history. So the Host still checks **before** it invokes:

| Dispatch | When |
|---|---|
| Tool | Host is about to `POST /api/tools/{name}` — agent loop, Apps `openApp`, proxy, or any other Host outbound tool call |
| Skill | Host is about to activate a skill (`load_skill` / playbook). Leaf tools in that playbook still pass the tool check |

Denied → `{ "ok": false, "error": "function_denied", "function_id": "..." }` (HTTP **403** on the proxy). Do not forward. The AIPP never sees the request.

### 6.4 Failure

| Layer | user-one unreachable |
|---|---|
| Context filter | **fail open** — an authority outage must not silently empty the product |
| Dispatch gate | **fail closed** for known-registered functions |

Do **not** re-implement any of this in widgets, chat controllers, or AIPPs.

---

## 7. First instance

`world` (world-entitir) declares `requires_authority: true` on `world_list_view` and registers it as `world::world_list_view` with **`gates_app=false`**. Only that tool needs a grant; the rest of world-entitir — decisions, queries, HR onboarding flows — stays open because it is unregistered.

In the default `12th` org the system **admin** role holds this function (`subjectKind=role`, `subjectId=12th-admin`). Users are global and may belong to many orgs; only an existing org membership can receive roles under that org. Grants may also be created with `assign_function` for a single user after `find_user`.

---

## 8. Verification

- Shared `AippFunctionAuthoritySpec` exposes wire names only and declares no methods.
- The authority provider validates function IDs, responses, ownership, and grant behavior in its own tests.
- Host tests: unregistered tools still pass; registered + no grant → 403
