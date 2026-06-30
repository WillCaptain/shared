# LLM provider configuration — Host contract

**Audience:** Host integrators (world-one), AIPP developers, ones-shell developers.

**Discovery:** [`INDEX.md`](INDEX.md) → this file.

**Related:** [`user-identity.md`](user-identity.md) (user id resolution), [`host-url.md`](host-url.md) (Host base URL), [`host-injection.md`](host-injection.md) (bindings — **does not** carry LLM credentials), [`configuration.md`](configuration.md) (AIPP business config — **does not** carry LLM credentials), [`host-runtime.md`](host-runtime.md) (agent loop).

---

## 1. Motivation

Every LLM call in the stack — the Host main agent loop **and** AIPP-internal analysis (e.g. note-one ingest, world-entitir autonomous explore) — needs the same three provider fields:

| Field | Purpose |
|-------|---------|
| `api_key` | Provider secret |
| `base_url` | OpenAI-compatible API root (e.g. `https://api.deepseek.com/v1`) |
| `model` | Model name (e.g. `deepseek-chat`) |

Today these are duplicated per process (`world-one.llm.*`, `entitir.llm.*`, `note-one.llm.*`, env vars, shared JSON files). That fragments resolution, breaks per-user policy, and scatters secrets.

**This spec centralizes LLM provider configuration on the Host (world-one).** The Host is the single authority; AIPP apps **pull** the effective config when they need to call an LLM themselves.

---

## 2. Ownership and boundaries

| Concern | Owner | Mechanism |
|---------|-------|-----------|
| LLM provider credentials + model | **Host (world-one)** | This spec — `GET /api/llm-config` |
| Instance-wide default (all users) | Host | Instance store (see §5) |
| Per-user personal override | Host, seeded by ones-shell | Per-user store (see §5) |
| Host agent loop model | Host | Same resolution as §4 |
| AIPP business config (world_id, URLs, …) | AIPP | [`configuration.md`](configuration.md) |
| Runtime env (`production` / `staging`) | Host | [`host-injection.md`](host-injection.md) |
| Host base URL | Each process locally | [`host-url.md`](host-url.md) |

### 2.1 What must NOT hold LLM credentials

- **AIPP `configuration.values`** — no `llm.api_key`, `LLM_*`, or provider URLs. See [`configuration.md`](configuration.md) §9.
- **`PUT /api/host/bindings`** — no `api_key`, `base_url`, or `model`. Bindings stay `host_id` / `app_id` / `env` only.
- **AIPP `application.yml` / env as the long-term source** — allowed only as a **dev fallback** when the Host is unreachable (see §4.3); production AIPPs MUST resolve via Host.
- **ones-shell `settings.json` plaintext** — personal keys MUST use OS keychain (see §7).

---

## 3. Resolution model

For a given **`user_id`**, the Host returns the **effective** config:

```
1. Per-user store     — if user_id is non-blank AND a personal config exists for that user
2. Instance default   — Host-wide default (operator / single-user setting)
3. Environment fallback — LLM_API_KEY, LLM_BASE_URL, LLM_MODEL (dev / headless only)
```

**Per-user replaces instance default** for that user. There is no merge: the first tier that yields a complete config wins.

A config is **complete** when all three of `api_key`, `base_url`, and `model` are non-blank after resolution.

### 3.1 `user_id` sources (identity-first)

Resolution requires a real user id. Sources, in order:

| Caller | How `user_id` is supplied |
|--------|---------------------------|
| Host agent loop | Session's resolved user id (from chat request or `get_user`) |
| AIPP tool handler | `_context.userId` on `POST /api/tools/{name}` |
| ones-shell → Host | `user_id` on `POST /api/chat` and on config write APIs |
| Direct `GET /api/llm-config` | Query `user_id` or header `X-User-Id` |

Until a user management AIPP exists, implementations MAY use the protocol stub from [`user-identity.md`](user-identity.md) (`id: "001"`). Hosts MUST NOT hardcode `"default"` as the long-term user id.

**Phase 1 prerequisite (no LLM API changes yet):** propagate real `user_id` end-to-end — ones-shell → `POST /api/chat` → session loop → `_context.userId`. See [`docs/llm-config-migration.md`](../docs/llm-config-migration.md).

---

## 4. Host endpoints (normative)

All paths are on the **Host** base URL ([`host-url.md`](host-url.md)). These are **not** AIPP app endpoints.

### 4.1 `GET /api/llm-config` — effective config (read)

Returns the resolved LLM config for the requested user (or instance default when `user_id` is omitted).

**Request**

| Mechanism | Field | Required | Notes |
|-----------|-------|----------|-------|
| Query | `user_id` | optional | Stable id from [`user-identity.md`](user-identity.md) |
| Header | `X-User-Id` | optional | Same as query; query wins if both present |
| Header | `Authorization: Bearer <token>` | required when transport auth enabled (§7.3) | Token from `~/.ones/host.json` |

**Success — `200`**

```json
{
  "ok": true,
  "source": "user",
  "user_id": "001",
  "config": {
    "api_key": "sk-…",
    "base_url": "https://api.deepseek.com/v1",
    "model": "deepseek-chat",
    "timeout_seconds": 120,
    "vision_mode": "auto"
  }
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `ok` | boolean | yes | `true` on success |
| `source` | string | yes | `user` \| `instance` \| `env` — which tier won |
| `user_id` | string | no | Echo of resolved user; omitted when anonymous instance/env resolution |
| `config.api_key` | string | yes | Full secret — callers use it to call the provider |
| `config.base_url` | string | yes | OpenAI-compatible root URL |
| `config.model` | string | yes | Model id |
| `config.timeout_seconds` | integer | no | HTTP timeout; default `120` |
| `config.vision_mode` | string | no | `auto` \| `on` \| `off` — Host vision gating ([`client-execution.md`](client-execution.md)) |

**Not configured — `200` with `ok: false`**

```json
{
  "ok": false,
  "error": "llm_not_configured",
  "message": "No complete LLM config at user, instance, or env tier."
}
```

AIPPs MUST treat this like today's `llm_not_configured` tool errors — surface a clear message, do not silently fall back to a different provider.

**Unauthorized — `401`** when transport auth is enabled and token is missing or invalid.

### 4.2 `PUT /api/llm-config/instance` — instance default (write)

Operator / Host settings UI. Replaces the instance-wide default used when no per-user config exists.

**Request body**

```json
{
  "api_key": "sk-…",
  "base_url": "https://api.deepseek.com/v1",
  "model": "deepseek-chat",
  "timeout_seconds": 120,
  "vision_mode": "auto"
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `api_key` | yes | Stored encrypted at rest (§7.1) |
| `base_url` | yes | |
| `model` | yes | |
| `timeout_seconds` | no | |
| `vision_mode` | no | |

**Response:** `{ "ok": true }` or `{ "ok": false, "error": "…" }`.

**Side effects:** Host SHOULD invalidate active agent loops and reload skill caches (same as today's settings save).

**Compatibility:** world-one's existing `POST /api/settings` (LLM fields) maps to this endpoint during migration; new implementations SHOULD prefer `PUT /api/llm-config/instance`.

### 4.3 `PUT /api/llm-config/user` — personal override (write)

ones-shell (or future user profile AIPP) sets the **personal** config for one user. This **replaces** the instance default for that user only.

**Request body**

```json
{
  "user_id": "001",
  "api_key": "sk-…",
  "base_url": "https://api.deepseek.com/v1",
  "model": "deepseek-chat",
  "timeout_seconds": 120,
  "vision_mode": "auto"
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `user_id` | yes | Must match authenticated / session user when auth is enabled |
| `api_key` | yes | Encrypted at rest (§7.1) |
| `base_url` | yes | |
| `model` | yes | |

**Delete personal override:** `DELETE /api/llm-config/user?user_id=001` → user falls back to instance default.

### 4.4 `GET /api/llm-config/instance` — masked read (settings UI)

For Host settings UI only. Returns instance config with **`api_key` masked** (e.g. `sk-****abcd`).

```json
{
  "ok": true,
  "config": {
    "api_key_masked": "sk-****abcd",
    "base_url": "https://api.deepseek.com/v1",
    "model": "deepseek-chat",
    "timeout_seconds": 120,
    "vision_mode": "auto"
  }
}
```

Personal user settings UI in ones-shell reads/writes via shell-local keychain + `PUT /api/llm-config/user`; it MUST NOT rely on masked reads for the user's own key (shell already holds it).

### 4.5 `POST /api/llm-config/test` — connectivity check

Optional. Body same shape as §4.2 (or `{ "user_id": "…" }` to test resolved config). Host performs a minimal provider call (e.g. single-token completion). Response:

```json
{ "ok": true, "latency_ms": 842 }
```

---

## 5. Persistence layout (Host)

Normative paths on the Host machine:

| Tier | Path | Encryption |
|------|------|------------|
| Instance default | `~/.worldone/llm/instance.json` (or legacy `~/.worldone-config.json` `llm` section during migration) | §7.1 |
| Per-user | `~/.worldone/llm/users/{user_id}.json` | §7.1 |
| Transport token | `~/.ones/host.json` → `host_access_token` | not secret-equivalent to LLM key; rotate independently |

Legacy note: existing `~/.worldone-config.json` combined settings file MAY be read as instance default until migrated.

---

## 6. Consumer patterns

### 6.1 Host agent loop

Before each LLM turn, the Host resolves config via §3 for the session's `user_id` and passes it to `LLMCaller`. The loop MUST NOT read `LLM_*` env directly except as tier-3 fallback inside the resolver.

### 6.2 AIPP app (internal LLM call)

When an AIPP needs its own LLM (autonomous explore, note ingest, etc.):

1. Resolve `host_base_url` — [`host-url.md`](host-url.md).
2. Read `user_id` from Host bindings context, tool `_context.userId`, or configuration-derived stable id (documented per app).
3. `GET {host_base_url}/api/llm-config?user_id={user_id}` with transport auth if enabled.
4. On `ok: true`, build provider client from `config`.
5. On `ok: false`, return a structured error — do not read `entitir.llm.*` / `note-one.llm.*` in production.

**Caching:** AIPP MAY cache the resolved config in memory for the process lifetime; MUST re-fetch after Host settings change signal or TTL ≤ 5 minutes.

### 6.3 ones-shell personal settings

```
User enters key in shell Settings UI
  → store in OS keychain (macOS safeStorage / platform equivalent)
  → PUT Host /api/llm-config/user { user_id, api_key, base_url, model }
  → Host encrypts at rest

On chat:
  shell resolves user_id (get_user)
  → POST /api/chat { session_id, message, user_id }
  → Host loop resolves LLM via §3 for that user_id
```

Shell MUST send `user_id` on every chat request once identity is available.

### 6.4 Deprecation of per-app LLM config keys

| Legacy | Replacement |
|--------|-------------|
| `world-one.llm.*` / `POST /api/settings` LLM fields | §4.2 / §4.4 |
| `entitir.llm.*`, `LLM_*` in world-entitir | §6.2 |
| `note-one.llm.*`, `shared-config-file: ~/.worldone-config.json` | §6.2 |
| Per-world `OntologyWorld.llmConfig()` override | **Discouraged** — use Host resolution; if retained, MUST override only after §6.2 fetch fails and MUST be documented in app README |

---

## 7. Security

### 7.1 Encryption at rest

- Instance and per-user JSON files MUST be stored with **`api_key` encrypted**.
- Master key material MUST come from the **Host machine OS keychain** (or equivalent), not from a file adjacent to the ciphertext.
- File mode `0600` on all LLM config paths.

### 7.2 ones-shell keychain

- User personal `api_key` MUST be stored in **OS keychain** before optional push to Host.
- Plaintext API keys MUST NOT appear in `settings.json`, logs, or crash reports.

### 7.3 Transport authentication

`GET/PUT/DELETE /api/llm-config*` MUST require authentication when the Host listens beyond trusted localhost.

**Token contract** — extend `~/.ones/host.json`:

```json
{
  "host_base_url": "http://127.0.0.1:8090",
  "host_access_token": "<random>"
}
```

- AIPP processes and ones-shell include `Authorization: Bearer <host_access_token>` on LLM config requests.
- **Development exception:** when `host_base_url` is loopback **and** token is absent, Host MAY allow unauthenticated access (log a warning once). Production deployments MUST set a token.

TLS (`https://`) is REQUIRED when `host_base_url` is not loopback.

### 7.4 Logging and exposure ceiling

- MUST NOT log full `api_key`, request bodies containing keys, or LLM config responses.
- Masked reads (§4.4) for UI only.
- **Exposure note:** `GET /api/llm-config` returns the raw key to authorized callers by design (AIPPs call the provider themselves). This is the accepted trade-off vs. a Host LLM proxy. A future Host proxy MAY be added without changing AIPP tool contracts.

---

## 8. Verification

### 8.1 Java (planned)

```java
AippLlmConfigSpec spec = new AippLlmConfigSpec();
spec.assertValidLlmConfigResponse(getResponse);
spec.assertValidLlmConfigInstancePutRequest(putBody);
spec.assertValidLlmConfigUserPutRequest(putBody);
```

Implement in `org.twelve.aipp.AippLlmConfigSpec` (not yet shipped — doc-first in v2.9).

### 8.2 Host smoke test

```bash
# Instance default
curl -s -H "Authorization: Bearer $TOKEN" \
  "$HOST/api/llm-config" | jq .

# Per-user
curl -s -H "Authorization: Bearer $TOKEN" \
  "$HOST/api/llm-config?user_id=001" | jq .
```

Expect `ok: true`, non-empty `config.model`, `source` matching the tier under test.

### 8.3 AIPP compliance

Apps that call an LLM MUST have an integration test proving they fetch from Host (or return `llm_not_configured` when Host has no config). See [`verify.md`](verify.md) § LLM config (2.9).

---

## 9. Implementation phases

See [`docs/llm-config-migration.md`](../docs/llm-config-migration.md) for the cross-repo rollout checklist. Summary:

| Phase | Deliverable |
|-------|-------------|
| **0 — Doc** | This spec + migration plan (current) |
| **1 — Identity** | Real `user_id` on chat + `_context.userId` |
| **2 — Host resolver** | Encrypted stores + `GET /api/llm-config` + instance PUT |
| **3 — Consumers** | world-one loop, world-entitir, note-one migrate off local LLM config |
| **4 — Shell** | Keychain + personal PUT + settings UI |
| **5 — Hardening** | Transport auth enforced, assert* gate, remove legacy paths |

---

## 10. FAQ

**Why not inject LLM config via `PUT /api/host/bindings`?**  
Bindings are push-on-install for long-lived workers and change rarely. LLM config is pull-on-demand, user-scoped, and must not force reinstall when a user updates their key.

**Why not put LLM settings in AIPP `configuration`?**  
Configuration is app-owned business data persisted by the AIPP. LLM credentials are Host-owned, shared across apps, and user-scoped — different lifecycle and security model.

**Does the Host proxy LLM calls?**  
Not in v2.9. Apps receive the key via `GET /api/llm-config` and call the provider directly. A proxy endpoint MAY be added later as an optional optimization.

**What about per-app model choice?**  
The effective model is Host-resolved. Apps SHOULD NOT hardcode model names except as dev fallbacks. Future extension: optional `?purpose=` query param for Host-side routing (not in v2.9).
