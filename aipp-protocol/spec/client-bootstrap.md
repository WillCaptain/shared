# Client package bootstrap — Once launch install flow

**Audience:** Host developers, ones-shell developers, AIPP developers.

**Depends on:** [`client-execution.md`](client-execution.md) §8 (dual-surface, `client_package`, handshake, blacklist).

**Discovery:** [`INDEX.md`](INDEX.md) → this file.

---

## 1. Scope

This spec defines how a **desktop shell (Once / ones-shell)** prepares **client packages** at launch — before the user hits a tool that needs them.

| In scope | Out of scope |
|----------|----------------|
| Skills stay **server-only** | User-management filtering of which AIPPs a user may use (future) |
| **Tier-1** builtins (`terminal`, `filesystem`, …) — always embedded in Once | Installing skills or playbooks on the client |
| **Tier-2 `client_package`** download + local process (jar) | Replacing dual-surface **server fallback** with agent hide (see §5) |
| Launch catalog + confirm + install / decline | Plugin UI details |

---

## 2. Terminology

| Term | Meaning |
|------|---------|
| **Skill** | Multi-step playbook served by AIPP `GET /api/skills` — **Host only**, never on client |
| **Tool** | Atomic capability in `GET /api/tools` |
| **client-only tool** | `execution_surface: "client"` — hidden from LLM without matching executor capability (INV-2) |
| **dual-surface tool** | `execution_surface: ["server","client"]` — always LLM-visible; prefers client when capability installed |
| **client_package** | Downloadable artifact that provides one **`client_capability`** locally (§8.3) |
| **Install record** | `{ app_id, capability, version, client_package, tools[] }` — not a full tool manifest on disk |

---

## 3. Launch bootstrap flow (normative)

Once **MUST** perform this sequence after `app.whenReady` and before relying on client-side parsing:

```
1. restoreInstalledPackages()     — re-launch jars from userData/installed.json
2. GET /api/client-install/catalog — Host registry + machine_id + local installed caps
3. For each package with status=missing:
     a. One native confirm (batch list OK)
     b. Accept  → download + launch each (failures allowed per package)
     c. Decline → POST /api/client-install/reject per app_id (blacklist)
4. registerClientExecutor()       — advertise capabilities + installed_client_apps
5. workspace / sync bootstrap     — may now use local parse_file
```

**Requirements:**

- **Failure allowed:** a failed download or health timeout for one package does not block Once startup.
- **Decline allowed:** closing the Tools window skips install; packages stay `missing` and the window reappears on next launch until installed. Use menu **Tools** to install later.
- **No silent install:** bootstrap shows the Tools install window when there are `missing` packages (user selects which to download).
- **Idempotent:** second launch skips the Tools window when all packages are `installed` (blacklisted packages are not auto-prompted).

---

## 4. Host catalog API

### `GET /api/client-install/catalog`

Query parameters:

| Param | Required | Description |
|-------|----------|-------------|
| `machine_id` | yes | Stable id from Once `userData/machine-id` |
| `installed_capabilities` | no | Comma-separated capabilities already running locally (e.g. `std.file.parse.v1`) |

Response:

```json
{
  "ok": true,
  "machine_id": "m-7f3a…",
  "needs_bootstrap": true,
  "packages": [
    {
      "app_id": "note-one",
      "capability": "std.file.parse.v1",
      "status": "missing",
      "tools": ["parse_file"],
      "client_package": {
        "app_id": "note-one",
        "capability": "std.file.parse.v1",
        "runtime": "jar",
        "version": "0.7.0",
        "url": "http://127.0.0.1:8096/api/client-package/std.file.parse.v1.jar",
        "sha256": "…",
        "health": "/api/health"
      }
    }
  ],
  "missing": [ "… same rows with status=missing only …" ]
}
```

**`status` values:**

| Status | Meaning |
|--------|---------|
| `installed` | `installed_capabilities` contains this capability |
| `blacklisted` | User declined on this `(machine_id, app_id)` |
| `missing` | Required by registry, not installed, not blacklisted |

**Catalog source:** dedupe all tools in the Host registry that declare a non-empty `client_package` with the same `(app_id, capability)`. Attach `tools[]` listing tool names that reference the package.

---

## 5. Agent visibility (disable list)

| Tool kind | Package missing / declined | LLM sees tool? | Execution |
|-----------|----------------------------|----------------|-----------|
| **client-only** | no capability | **No** (INV-2) | — |
| **dual-surface** | package missing | **Yes** | Server fallback |
| **dual-surface** | user declined (blacklisted) | **Yes** | Server fallback |

There is **no separate “disable list” object** passed to the agent. Host **filters the tool list** before the LLM:

- **client-only** tools without an advertised capability are omitted.
- **dual-surface** tools remain visible so server-side handlers still work.

Bootstrap reduces “missing capability” cases early; it does **not** hide dual-surface tools when the user declines local install.

---

## 6. Persistence (client)

| File | Content |
|------|---------|
| `userData/client-packages/installed.json` | Accepted packages (url, sha256, version, capability, app_id) |
| `userData/client-packages/blacklist.json` | Declined `app_id` list (local mirror) |
| `userData/machine-id` | Stable machine id |

Host mirrors blacklist via `POST /api/client-install/reject`.

---

## 7. Verification

- Host exposes `GET /api/client-install/catalog` with deduped packages.
- Once calls catalog on launch when world-one URL is reachable.
- Missing packages trigger one confirm; accept installs; decline blacklists.
- After bootstrap, executor handshake includes new capabilities in `capabilities` + `installed_client_apps`.

See [`verify.md`](verify.md) and `ClientInstallCatalogTest` (world-one).
