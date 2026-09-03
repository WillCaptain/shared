# User identity — `get_user` tool contract

**Audience:** AIPP developers, Host integrators, ones-shell developers.

**Discovery:** [`INDEX.md`](INDEX.md) → this file.

---

## 1. Motivation

Application data may be scoped by **user** and **machine**. Callers need a stable user id before reading or writing identity-scoped data.

The active identity provider advertises `get_user`, connects to its account authority, and converts the authority response into the canonical AIPP profile shape. Consumer AIPPs MUST NOT synthesize profiles or provide a production identity stub. Provider selection is deployment and registration policy, not part of this shared contract.

The Host validates the active principal through the configured provider before invoking identity-scoped consumer tools. It forwards the resulting trusted user id in invocation `_context.userId`; consumers validate that trusted principal and reject conflicting model-supplied `user_id` values.

---

## 2. Tool: `get_user`

| Field | Value |
|-------|--------|
| Name | `get_user` |
| Args | `{}` (no required parameters) |
| Provider | One provider selected by Host configuration or registration policy |
| `visibility` | `["host", "llm"]` recommended |

The Host forwards the caller's `Authorization` header when invoking the provider. The provider resolves it against its configured external account authority. Missing/invalid authorization fails with `401`; authority failure fails closed (for example `503 identity_authority_unavailable`).

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

If the provider is unavailable or the external account authority cannot validate the caller, the Host MUST treat identity resolution as unavailable/unauthorized. It MUST NOT fall back to a fixed user id or accept a model-supplied identity as trusted.

Per-user LLM provider settings are resolved on the Host by `user_id` — see [`llm-config.md`](llm-config.md).

### 2.3 Host chat requests

Host chat requests SHOULD include the active account credential and MAY include `user_id` (or `userId`) as a claim. The Host validates the active account through the provider's `get_user`; only the validated id becomes trusted invocation context. A supplied id that conflicts with the provider response MUST be rejected.

---

## 3. Verification

Use `AippIdentityContract` for protocol field and capability names. Provider and Host tests own response validation and provider-selection policy; shared contains no implementation or concrete-provider assertion.

See [`verify.md`](verify.md).
