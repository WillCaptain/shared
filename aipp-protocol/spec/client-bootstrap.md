# Client package bootstrap — Once launch install flow

**Audience:** Host developers, ones-shell developers, AIPP developers.

**Depends on:** [`client-execution.md`](client-execution.md) §8 (dual-surface, `client_package`, handshake, blacklist).

**Related:** [`client-packages.md`](client-packages.md) — which path an AIPP author uses. [`aipp-extension-packs.md`](aipp-extension-packs.md) — pack overlay on a `target_app`.

**Discovery:** [`INDEX.md`](INDEX.md) → this file.

---

## 1. Scope

This spec defines how a **desktop shell (Once / ones-shell)** prepares **client packages** at launch — before the user hits a tool that needs them.

| In scope | Out of scope |
|----------|----------------|
| Skills stay **server-only** | User-management filtering of which AIPPs a user may use (future) |
| **Tier-1** builtins (`terminal`, `filesystem`, …) — always embedded in Once | Installing skills or playbooks on the client |
| **Tier-2 `client_package`** download + local process (jar) | Replacing dual-surface **server fallback** with agent hide (see §5) |
| Launch catalog + Plugin confirm before download / upgrade | Mid-session dual-surface offer UX details |

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
3. Annotate needs_install:
     - status=missing / blacklisted → needs download
     - local installed.json version ≠ catalog version → needs upgrade
4. If any needs_install:
     a. Open the Plugin window with the missing and outdated rows selected
     b. Download or upgrade only the rows the user confirms
     c. On package failure → leave that row failed; Once startup continues
5. registerClientExecutor()       — advertise capabilities + installed_client_apps
6. workspace / sync bootstrap     — may now use a local package such as parse_file
```

**Requirements:**

- **Confirm before download:** Once **MUST NOT** silently download or replace a package. The Plugin window is the install and upgrade path. Menu **Tools** opens the same window.
- **Detect on launch:** a missing package, or a catalog version newer than the local install, **MUST** open that window once per launch.
- **Failure allowed:** a failed download or health timeout for one package does not block Once startup.
- **Idempotent:** a later launch skips the window when nothing is missing or outdated.

Menu **Tools → Manage Local Tools…** opens the same Tools form anytime.

---

## 4. Host catalog API

### `GET /api/client-install/catalog`

Query parameters:

| Param | Required | Description |
|-------|----------|-------------|
| `machine_id` | yes | Stable id from Once `userData/machine-id` |
| `installed_capabilities` | no | Comma-separated capabilities already running locally (e.g. `std.file.parse.v1`) |
| `platform` | no | Executor OS: `darwin` / `win32` / `linux`, or `mac` / `windows`. The Host drops rows whose `platforms` do not include it. Blank keeps every row |

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
| `blacklisted` | User declined on this `(machine_id, app_id)` (legacy / mid-session reject) |
| `missing` | Required by registry, not installed, not blacklisted |
| `outdated` | Once-side only: local manifest version ≠ catalog version (treated as needs_install) |

**Catalog source:** dedupe all tools in the Host registry that declare a non-empty `client_package` with the same `(app_id, capability)`, then union administrator extension-pack client tools. Attach `tools[]` listing tool names that reference the package. Authoring for both sources is [`client-packages.md`](client-packages.md).

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

**Once session disable** (failed auto-download) hides/blocks the related AIPP in the Once-hosted app list for that process only; it does not change Host tool visibility for the LLM.

---

## 6. Persistence (client)

| File | Content |
|------|---------|
| `userData/client-packages/installed.json` | Installed packages (url, sha256, version, capability, app_id) |
| `userData/client-packages/blacklist.json` | Declined `app_id` list (local mirror; legacy mid-session reject) |
| `userData/machine-id` | Stable machine id |

Host mirrors blacklist via `POST /api/client-install/reject`. Session-disabled apps after download failure are **not** written to disk.

---

## 7. Verification

- Host exposes `GET /api/client-install/catalog` with deduped packages.
- Once calls catalog on launch when world-one URL is reachable.
- Needed packages open the Plugin window; download and upgrade wait for the user. A failed package does not block startup.
- After bootstrap, executor handshake includes new capabilities in `capabilities` + `installed_client_apps`.

See [`verify.md`](verify.md) and `ClientInstallCatalogTest` (world-one).
