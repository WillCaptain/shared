# Client packages and extension packs

**Audience:** AIPP developers who need code to run on Once.

**Depends on:** [`client-execution.md`](client-execution.md) §8, [`client-bootstrap.md`](client-bootstrap.md), [`aipp-extension-packs.md`](aipp-extension-packs.md).

**Gate:** `AippAppSpec.assertValidClientExecutionFields` for a tool-owned `client_package`.

**Discovery:** [`INDEX.md`](INDEX.md) → this file.

Once does not embed an AIPP’s local program. Two standards put that program on the machine. They share one catalog and one Plugin window. They are not the same authoring path.

| | Tool-owned `client_package` | Extension pack |
| --- | --- | --- |
| Who publishes it | The AIPP, on a tool in `GET /api/tools` | An operator uploads a zip in Administrator → Plugin |
| Example | note-one `parse_file` (`std.file.parse.v1`) | `safari-bookmarks`, `local-reminders`, `local-clock` |
| What it extends | That AIPP’s own tool | The pack’s `target_app`. The packs shipped for desktop apps use `computer-use-one` |
| Once | Lists it, and downloads after the user confirms | The `client/` half joins the same catalog |

---

## 1. Tool-owned client package

Any AIPP may declare this. It is not a parser-only feature. Declare it when **this AIPP** owns the local program that performs one of its tools.

### 1.1 Manifest

The tool **MUST** include `client` in `execution_surface` and a non-empty `client_capability`. A `client_package` on a server-only tool is rejected.

```json
{
  "name": "parse_file",
  "execution_surface": ["server", "client"],
  "client_capability": "std.file.parse.v1",
  "client_package": {
    "app_id": "note-one",
    "capability": "std.file.parse.v1",
    "runtime": "jar",
    "version": "1.0.0",
    "artifact": "std.file.parse.v1.jar",
    "url": "https://host.example/api/proxy/app/note-one/api/client-package/std.file.parse.v1.jar",
    "sha256": "<hex>",
    "health": "/api/health",
    "invoke": "/api/invoke",
    "launch": { "args": ["--server.port=${PORT}"] }
  }
}
```

| Field | Rule |
| --- | --- |
| `execution_surface` | `client`, or `["server","client"]` for a dual-surface tool |
| `client_capability` | Stable id the executor advertises after install. **MUST** equal `client_package.capability` |
| `client_package.runtime` | `jar`, `node`, or `binary`. `host` is not valid on an AIPP tool manifest |
| `client_package.artifact` | **MUST** be non-blank. Use the file name Once stores (`std.file.parse.v1.jar`) |
| `client_package.url` | Address Once fetches. **MUST** be set or the catalog row cannot download |
| `client_package.version` | **SHOULD** be set. Once marks the row outdated only when the catalog version is newer |
| `client_package.sha256` | **SHOULD** be set. Once rejects a mismatched download |
| `client_package.platforms` | Optional. `mac`, `windows`, `linux`. Omitted or empty means every desktop OS |

`AippAppSpec.assertValidClientExecutionFields` checks the surface, the capability, `runtime`, and a non-blank `artifact`. Once downloads `url`.

### 1.2 Dual-surface or client-only

| Shape | When the package is missing | What the model sees |
| --- | --- | --- |
| `["server","client"]` | The call runs on the AIPP server. Once still offers install | The tool stays visible |
| `client` only | The tool is hidden until Once advertises the capability | Nothing, until install |

Use dual-surface when the server can do the same job, as `parse_file` does. Use client-only when the work can only happen on the user’s machine.

A connected Once that does not yet advertise the capability receives an install offer. A dual-surface call **MUST NOT** wait for that offer. The server result returns, and the install is a later upgrade.

### 1.3 What the local process must do

For `runtime: jar`, Once runs `java -jar` on a loopback port. The process **MUST**:

- answer `GET <health>` with HTTP 200 when ready (`health` defaults to `/health`);
- answer `POST <invoke>` with the same result shape as `POST /api/tools/{name}` (`invoke` defaults to `/invoke`).

The body of `/invoke` is `{ "tool", "arguments" }`. Once does not know the tool’s domain. It forwards the client call to that process.

Once does not ship a Java runtime. A `jar` package needs a `java` binary already on the machine (`JAVA_HOME` or `PATH`). If Java is missing, install fails with `java_missing`.

`node` and `binary` are legal manifest runtimes. Ship them only when Once has a launcher for that runtime. Do not invent a fourth runtime on the AIPP tool.

### 1.4 How Once finds it

1. The AIPP registers with the Host.
2. The Host catalogs every registered tool whose `client_package` is non-empty. Rows with the same `(app_id, capability)` collapse into one package. `tools[]` lists the tool names.
3. On launch, Once calls `GET /api/client-install/catalog?machine_id=…&installed_capabilities=…&platform=…`.
4. The Host drops a row whose `platforms` does not include that OS. A blank platform query keeps the row, so an old client still sees it.
5. Once compares versions. A missing package, or a catalog version newer than the local one, opens the Plugin window with those rows selected.
6. Download and upgrade run only after the user confirms. Once **MUST NOT** silently download or replace a package.
7. After a successful install, Once reports `POST /api/client-install/installed` and advertises `client_capability` on the next executor handshake.

The package bytes stay on the AIPP (or behind the Host proxy). They are not compiled into the Once installer.

---

## 2. Extension pack

Use a pack when the ability is optional and **must not** be compiled into the base AIPP. `computer-use-one` stays a generic desktop executor. Safari bookmarks, the local clock, and local reminders are packs on top of it, not tools inside that jar.

`target_app` is any installed AIPP `app_id`. A pack **MUST** name the AIPP it extends. The desktop packs in this product use `computer-use-one`.

An operator publishes the zip in Administrator → Plugin. The Host stores it and merges it. Once does not upload packs.

### 2.1 Layout

```text
{pack-id}/
  package.json
  skills/{skill_id}/SKILL.md          # optional
  client/                             # optional; required for a Once download
    client-package.json
    tools.json
    host-handler.js | app.jar
```

`package.json`:

| Field | Required | Role |
| --- | --- | --- |
| `id` | yes | Stable pack id |
| `version` | yes | Semver. Once offers Upgrade when the catalog version is newer |
| `title` | yes | Plugin window label |
| `target_app` | yes | AIPP that receives the skills and the description append |
| `description` | no | Administrator UI only. Not router text |
| `capability_extension` | no | One or two sentences appended to the effective description of `target_app` |
| `platforms` | should | `["mac"]`, `["windows"]`, or `["mac","windows"]`. Omitted means every desktop OS |

A pack **MAY** ship any combination of `skills/`, `client/`, and `capability_extension`. See [`aipp-extension-packs.md`](aipp-extension-packs.md) for the description merge and the install gate.

### 2.2 Client half

`client/client-package.json` is the Once package. `client/tools.json` is `{ "tools": [ … ] }`. Each tool is a normal tool object (`name`, `description`, `parameters`, `side_effect`). The Host marks those tools `execution_surface: client` and attaches this `client_package`.

| `runtime` | What Once runs |
| --- | --- |
| `jar` | `java -jar`, same health / invoke contract as §1.3 |
| `host` | `host-handler.js` inside the Once process. `artifact` or `host.entry` names that file |

`host` is legal on a pack. It is not legal on an AIPP’s own `GET /api/tools` manifest.

`platforms` on `package.json` or on `client-package.json` filters the catalog. A Mac-only pack **MUST** set `["mac"]` so Windows Once never lists it. One cross-platform handler **MAY** set `["mac","windows"]` and branch on `process.platform` inside `host-handler.js`.

Skill-only packs have no `client/` directory. The Host indexes the skill at publish time. Once has nothing to download. `local-clock` is that shape: a macOS playbook over the built-in accessibility tools, with `platforms: ["mac"]`.

### 2.3 After publish

1. Skills are overlaid onto `target_app` without redeploying that AIPP.
2. The client half, when present, joins the same `GET /api/client-install/catalog` as §1.4.
3. `capability_extension` is appended for routing only when the gate in [`aipp-extension-packs.md`](aipp-extension-packs.md) §4 passes. A pack with a client capability is announced only after this session’s Once advertises that capability.
4. Once opens the Plugin window for a missing or outdated client half. The user confirms the download. There is no silent install.

Do not put the pack’s product nouns into the base AIPP `app_description` or into Host prompts. Those nouns belong in `capability_extension` and in the pack’s skill and tool text.

---

## 3. Choose a path

| You are building… | Use |
| --- | --- |
| A tool this AIPP always owns, with an optional or required local program | §1, on `GET /api/tools` |
| An optional local app or playbook that a base AIPP such as `computer-use-one` must not hardcode | §2, an extension pack |
| A playbook with no new local program | A skill only. Either the AIPP’s own `resources/skills/`, or a skill-only pack |

Both downloadable shapes show up in the Once Plugin window. Built-in Once capabilities (`terminal`, `filesystem`, `browser`, and the rest of the computer-use contract) are neither. Those ship inside Once and are not catalog rows.
