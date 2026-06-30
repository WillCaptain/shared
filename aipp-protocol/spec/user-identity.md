# User identity — `get_user` tool contract

**Audience:** AIPP developers, Host integrators, ones-shell developers.

**Discovery:** [`INDEX.md`](INDEX.md) → this file.

---

## 1. Motivation

Workspace paths, preferences, and vault bindings are scoped by **user** and **machine**. Callers need a stable user id before reading or writing per-machine profile data in note-one (or future profile stores).

A dedicated **user management AIPP** (`user-one`, future) will own real identity and auth. Until it is registered, providers MAY return the **protocol default stub** documented below.

---

## 2. Tool: `get_user`

| Field | Value |
|-------|--------|
| Name | `get_user` |
| Args | `{}` (no required parameters) |
| Owner | Future `user-one` AIPP; interim stub allowed on note-one |
| `visibility` | `["host", "llm"]` recommended |

### 2.1 Success response

```json
{
  "ok": true,
  "user": {
    "id": "001",
    "name": "will"
  }
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `ok` | boolean | yes | `true` on success |
| `user.id` | string | yes | Stable user id for profile keys |
| `user.name` | string | yes | Display name |

### 2.2 Protocol default stub

When no user management AIPP is registered, implementations MUST return exactly:

```json
{ "ok": true, "user": { "id": "001", "name": "will" } }
```

Hosts and desktop shells MAY cache this id for the session but MUST re-fetch when identity may have changed (login flow, future).

Per-user LLM provider settings are resolved on the Host by `user_id` — see [`llm-config.md`](llm-config.md).

### 2.3 Host chat requests

`POST /api/chat` on world-one SHOULD include `user_id` (or `userId`) when the client knows the active user. When omitted, the Host resolves via `get_user` or falls back to the protocol default stub (§2.2).

---

## 3. Workspace tools (machine-scoped profile)

Per-machine workspace bindings live in **note-one** (not world-one). Tools:

| Tool | Required args | Response fields |
|------|---------------|-----------------|
| `get_workspace` | `user_id`, `machine_id` | `workspace` (absolute path or `null`), `default_suffix` (logical default, e.g. `"/once"`) |
| `set_workspace` | `user_id`, `machine_id`, `path` | `workspace` (normalized absolute path) |

`machine_id` is the stable per-machine id from the client executor handshake (`client-execution.md` §8.7) — not a raw MAC address.

**note-one default suffix:** `/once` (no OS prefix). The desktop shell resolves `{Documents}/once` on first bind when `workspace` is `null`.

---

## 4. Verification

Use `AippUserIdentitySpec.assertValidGetUserResponse` on tool responses.

See [`verify.md`](verify.md).
