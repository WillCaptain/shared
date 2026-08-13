# User identity — `get_user` tool contract

**Audience:** AIPP developers, Host integrators, ones-shell developers.

**Discovery:** [`INDEX.md`](INDEX.md) → this file.

---

## 1. Motivation

Workspace paths, preferences, and vault bindings are scoped by **user** and **machine**. Callers need a stable user id before reading or writing per-machine data in note-one.

The **user-one AIPP is the sole owner of `get_user` and user profile responses**. user-one connects to the external account authority, forwards the caller's authorization credential, and converts the authority's account response into the canonical AIPP profile shape. note-one and other consumer AIPPs MUST NOT advertise `get_user`, synthesize profiles, or provide a production identity stub.

The Host validates the active principal through user-one before invoking identity-scoped consumer tools. It forwards the resulting trusted user id in invocation `_context.userId`; consumers validate that trusted principal and reject conflicting model-supplied `user_id` values.

---

## 2. Tool: `get_user`

| Field | Value |
|-------|--------|
| Name | `get_user` |
| Args | `{}` (no required parameters) |
| Owner | `user-one` AIPP only |
| `visibility` | `["host", "llm"]` recommended |

The Host forwards the caller's `Authorization` header when invoking user-one. user-one resolves it against the configured external account authority. Missing/invalid authorization fails with `401`; authority failure fails closed (for example `503 identity_authority_unavailable`).

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

### 2.2 No production fallback

If user-one is unavailable or the external account authority cannot validate the caller, the Host MUST treat identity resolution as unavailable/unauthorized. It MUST NOT fall back to a fixed user id, accept a model-supplied identity as trusted, or ask note-one to manufacture a profile.

Per-user LLM provider settings are resolved on the Host by `user_id` — see [`llm-config.md`](llm-config.md).

### 2.3 Host chat requests

`POST /api/chat` on world-one SHOULD include the active account credential and MAY include `user_id` (or `userId`) as a claim. The Host validates the active account through user-one's `get_user`; only the validated id becomes trusted invocation context. A supplied id that conflicts with user-one's response MUST be rejected.

---

## 3. Workspace tools (machine-scoped profile)

Per-machine workspace bindings live in **note-one** (not user-one or world-one). note-one owns only these workspace profile tools and validates `_context.userId` before dispatch:

| Tool | Required args | Response fields |
|------|---------------|-----------------|
| `get_workspace` | `user_id`, `machine_id` | `workspace` (absolute path or `null`), `default_suffix` (logical default, e.g. `"/once"`) |
| `set_workspace` | `user_id`, `machine_id`, `path` | `workspace` (normalized absolute path) |

`machine_id` is the stable per-machine id from the client executor handshake (`client-execution.md` §8.7) — not a raw MAC address.

**note-one default suffix:** `/once` (no OS prefix). The desktop shell resolves `{Documents}/once` on first bind when `workspace` is `null`.

---

## 4. Verification

Use `AippUserIdentitySpec.assertUserOneOwnsGetUser` on the provider catalog and `assertValidGetUserResponse` on user-one tool responses. Consumer compliance tests SHOULD assert that note-one does not advertise `get_user`.

See [`verify.md`](verify.md).
