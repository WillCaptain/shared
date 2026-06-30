# LLM config migration — cross-repo rollout

**Normative contract:** [`spec/llm-config.md`](../spec/llm-config.md)

This document is the **implementation checklist** for moving LLM provider settings into the AIPP protocol Host contract. It is not normative; when it disagrees with `spec/llm-config.md`, the spec wins.

---

## Current state (2026-06)

| Component | Today | Problem |
|-----------|-------|---------|
| **world-one** | `WorldOneConfigStore` → `~/.worldone-config.json`; `POST /api/settings`; loop uses global config; `userId = "default"` | Single-user; no per-user tier |
| **ones-shell** | No LLM fields in `settings.json` | User cannot set personal key |
| **world-entitir** | `WorldEntitirLlmConfig` → `entitir.llm.*` / `LLM_*` env | Bypasses Host; optional per-world override |
| **note-one** | `note-one.llm.*` + `shared-config-file: ~/.worldone-config.json` | Duplicates Host file read |
| **aipp-protocol** | No LLM spec (until v2.9) | Fragmented ad-hoc patterns |

---

## Phase 0 — Documentation (this PR)

- [x] Add [`spec/llm-config.md`](../spec/llm-config.md)
- [x] Update [`spec/INDEX.md`](../spec/INDEX.md), README changelog, development skill router
- [x] Cross-link [`user-identity.md`](../spec/user-identity.md), [`configuration.md`](../spec/configuration.md), [`ontology-world-operation.md`](../spec/ontology-world-operation.md)
- [x] Review sign-off before code

---

## Phase 1 — Identity end-to-end (no LLM API yet) — **done**

**Goal:** Every session and tool call carries a real `user_id`.

| Repo | Task | Status |
|------|------|--------|
| **ones-shell** | Resolve `user_id` via `get_user` proxy; cache for session; expose via `getUserId` IPC | done |
| **world-one** | Accept `user_id` on `POST /api/chat`; pass to `WorldOneSessionStore.createLoop` | done |
| **world-one** | Ensure `GenericAgentLoop.buildContext()` emits real `_context.userId` | done |
| **world-one** | Frontend `index.html` tool calls: stop hardcoding `'default'` | done |
| **aipp-protocol** | Document `user_id` on chat in `user-identity.md` | done |

**Exit criteria:** `grep -r '"default"'` in session creation paths replaced; integration test chat with `user_id: "001"` propagates to proxied tool `_context`.

---

## Phase 2 — Host LLM resolver + encrypted store — **done**

**Goal:** Host exposes normative `GET /api/llm-config` and instance write.

| Repo | Task | Status |
|------|------|--------|
| **shared/aipp-protocol** | Add `AippLlmConfigSpec` + tests for response/put shapes | done |
| **world-one** | `LlmConfigService` — tiers: user → instance → env | done |
| **world-one** | Encrypted persistence: `~/.worldone/llm/instance.json`, `~/.worldone/llm/users/{id}.json`; master key from macOS Keychain or fallback file | done |
| **world-one** | `GET /api/llm-config`, `PUT /api/llm-config/instance`, `GET /api/llm-config/instance` (masked) | done |
| **world-one** | `PUT/DELETE /api/llm-config/user`, `POST /api/llm-config/test` | done |
| **world-one** | Map existing `POST /api/settings` LLM fields → encrypted instance store (compat) | done |
| **world-one** | Refactor agent loop + `FreePlanningController` to use resolver | done |
| **shared/aipp-protocol** | `HostAccessToken` helper for `~/.ones/host.json` | done |
| **world-one** | Loopback transport auth warn + Bearer enforcement when token set | done |

**Exit criteria:** Host loop calls provider using resolver; curl smoke tests pass; encrypted files contain no plaintext keys.

---

## Phase 3 — AIPP consumers migrate — **done**

**Goal:** No production AIPP reads local `*.llm.*` or shared JSON for credentials.

| Repo | Task | Status |
|------|------|--------|
| **shared/aipp-protocol** | Add `HostLlmConfigClient.fetch(hostBaseUrl, userId, token)` helper | done |
| **world-entitir** | Replace `WorldEntitirLlmConfig` with Host fetch in `OntologyAutonomousExploreService` | done |
| **note-one** | Host-only `resolveLlm`; removed `note-one.llm.*` yml | done |
| **memory-one** | Host-only `MemoryOneConfigurationService`; removed shared JSON / `memory-one.llm.*` | done |
| **world-one** | Agent loop, vision, plan critic use `resolve(userId)` not instance-only | done |

**Exit criteria:** Integration tests with Host down return `llm_not_configured`; with Host configured, apps call provider successfully.

---

## Phase 4 — ones-shell personal settings — **done**

**Goal:** User personal key in keychain; replaces instance default for that user.

| Repo | Task | Status |
|------|------|--------|
| **ones-shell** | Settings UI: api key, base URL, model (provider presets like world-one) | done |
| **ones-shell** | Store api key in `safeStorage`; base URL/model in `settings.json` (`llmUsers`) | done |
| **ones-shell** | On save: `PUT /api/llm-config/user` with Bearer token from `~/.ones/host.json` | done |
| **world-one** | `PUT/DELETE /api/llm-config/user` | done (Phase 2) |
| **world-one** | `POST /api/llm-config/test` accepts inline config (pre-save test) | done |

**Exit criteria:** User A and User B (different ids) get different effective configs from `GET /api/llm-config?user_id=…`.

---

## Phase 5 — Hardening and cleanup

| Repo | Task |
|------|------|
| **world-one** | Enforce transport auth on all `/api/llm-config*` when not loopback |
| **world-one** | Auth: verify token + optional `user_id` match on user PUT | done (`X-User-Id`) |
| **world-entitir** | Host-only `WorldEntitirLlmConfig`; removed `entitir.llm.*` yml | done |
| **note-one** | Host-only `resolveLlm`; removed `note-one.llm.*` yml | done |
| **memory-one** | Host-only; removed `memory-one.llm.*` / shared JSON | done |
| **aipp-protocol** | `verify.md` § LLM gate; wire assert* in consumer CI |
| **All** | Audit logs for accidental key leakage |

**Exit criteria:** Full verify gate green; legacy env documented as dev-only fallback.

---

## Risk register

| Risk | Mitigation |
|------|------------|
| Key in every AIPP process memory | Accepted v2.9 trade-off; document; future Host proxy optional |
| Loopback dev without token | Explicit dev exception in spec; warn in logs |
| Migration breaks existing `~/.worldone-config.json` users | Phase 2 reads legacy file as instance tier until explicit migrate command |
| Host down → all LLM features fail | Clear `llm_not_configured` errors; dev env fallback tier 3 |

---

## Suggested PR order

1. **aipp-protocol** — spec + migration doc (this change)
2. **world-one** — Phase 1 identity
3. **world-one** — Phase 2 resolver + endpoints
4. **shared/llm-shared** + **world-entitir** + **note-one** — Phase 3 (can parallelize)
5. **ones-shell** — Phase 4
6. **world-one** + **aipp-protocol** — Phase 5 assert* gate
