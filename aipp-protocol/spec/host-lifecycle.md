# AIPP ↔ Host Lifecycle (attach / refresh / liveness)

> **Discovery:** [`INDEX.md`](INDEX.md) → this file. Pairs with [`host-registration.md`](host-registration.md)
> (the install contract) and [`host-url.md`](host-url.md) (how the AIPP learns the Host URL).
> **Helper:** `org.twelve.aipp.host.AippHostLifecycle` (framework-agnostic, in aipp-protocol).
> **Spring:** `aipp-protocol-spring` auto-configuration (`AippHostAttachAutoConfiguration`).

A **standard AIPP manages its own presence in the Host app list**. The AIPP — not the Host — is
responsible for being present: it keeps a background **attach loop** that registers on launch and
periodically re-attaches, so a living AIPP always returns to the app list after a Host restart or a
stale eviction. **The Host never has to discover AIPPs.** No manual
`~/.ones/apps/{app_id}/manifest.json` editing is required.

Three principles:

1. **AIPP continuous attach** — the AIPP keeps (re)attaching to the Host; it does not assume one
   successful register is permanent.
2. **Host idempotent accept** — a re-attach from the same instance is a cheap liveness ack (no
   re-pull of tools/widgets).
3. **Host periodic probe** — the Host probes registered apps in the background, independent of
   anyone opening the app list.

---

## 1. Behaviors

| When | Who acts | Call | Effect |
|------|----------|------|--------|
| **Launch + every ~15s** | AIPP → Host | `POST {host}/api/registry/install` `{app_id, base_url, instance_id}` | App appears / stays in the app list |
| **Background (~30s)** | Host → AIPP | `GET {base_url}/api/tools` (throttled probe) | Liveness / `is_active` reflects reachability |
| **Shutdown** *(optional, discouraged)* | AIPP → Host | `DELETE {host}/api/registry/{app_id}?instance_id=…` | App removed immediately |

`base_url` is the **externally reachable address the Host uses to reach the AIPP** — not the Host URL.

---

## 2. Attach loop (AIPP-side)

On startup the AIPP starts a daemon loop that POSTs its identity to the Host. Because the Host may
start *after* the AIPP, the first attach **retries** while the Host is unreachable, and the loop
runs **off the main thread** so it never blocks app boot. After the first success it keeps
re-attaching on a fixed interval (~15s). It is best-effort: failure must not crash the app.

```bash
curl -X POST http://localhost:8090/api/registry/install \
  -H 'Content-Type: application/json' \
  -d '{"app_id":"note-one","base_url":"http://localhost:8096","instance_id":"<uuid-per-jvm>"}'
# first attach → {"success":true,"reloaded":true,"message":"App note-one installed successfully"}
# refresh      → {"success":true,"reloaded":false,"message":"App note-one refreshed"}
```

Each JVM generates a unique `instance_id` on startup and sends it on every attach. The continuous
loop is what brings a living AIPP back under management — the Host does not scan for AIPPs.

## 3. Idempotent accept (Host-side)

`POST /api/registry/install` is **idempotent**:

- **New / changed instance** (unknown app, or a different `base_url`/`instance_id`) → full install:
  the Host pulls `/api/app`, `/api/tools`, `/api/skills`, `/api/widgets`, persists a manifest (see
  [`host-registration.md`](host-registration.md) §1), and calls `PUT /api/host/bindings`. Response
  carries `"reloaded": true`.
- **Same instance already loaded** (matching `app_id` + `base_url` + `instance_id`) → lightweight
  ack: the Host just records proof-of-life (online + timestamp), **no re-pull**. Response carries
  `"reloaded": false`.

This makes the ~15s attach loop cheap while still healing the list after a Host restart (where the
first post-restart attach is treated as `reloaded:true`).

## 4. Liveness probe (Host-side)

The Host probes each non-builtin app's `GET /api/tools` **in the background on a fixed schedule**
(~30s), independent of anyone opening the app list, and also on app-list open (throttled, async).
It records the result as the app's online status and re-loads apps whose manifest exists but failed
to load at boot. An unreachable app is shown inactive/offline rather than removed. **AIPPs need no
extra client-side heartbeat beyond the attach loop** — just keep the core endpoints fast and
side-effect-free on GET.

> A hard crash (kill -9, OOM) stops the attach loop and fails the probe: the Host marks the app
> offline on the next probe tick.

## 5. Deregister on shutdown (optional, discouraged)

Long-lived AIPPs should **not** deregister on shutdown. Spring can run shutdown hooks while the JVM
is still serving (or during a partial shutdown), which would evict a replacement instance that has
already attached. Presence is healed by the attach loop + the Host liveness probe + the retained
`~/.ones/apps` manifest.

If you do deregister (e.g. an explicit user "log out"), the Host endpoint is idempotent and
instance-guarded: deregistering an unknown app still returns success, and a **stale** deregister
(old/absent `instance_id`) is ignored so a replacement instance stays in the list.

```bash
curl -X DELETE 'http://localhost:8090/api/registry/note-one?instance_id=<same-uuid>'
# → {"success":true,"message":"App note-one deregistered"}
```

---

## 6. Reference helpers

### Framework-agnostic (`aipp-protocol`)

`AippHostLifecycle` (pure `java.net.http`; wire from your own startup/shutdown hooks):

```java
var lifecycle = new AippHostLifecycle(hostBaseUrl, appId, selfBaseUrl);

// startup: persistent attach loop on a daemon thread
lifecycle.startAttachLoop();              // default: 30 initial attempts @5s, refresh @15s
// or: lifecycle.startAttachLoop(maxInitialAttempts, retryDelay, refreshInterval);

// shutdown: stop the loop only (no deregister)
lifecycle.stop();
```

### Spring Boot (`aipp-protocol-spring`) — **standard for all Java AIPPs**

Add the module to your app `pom.xml` and set the unified `aipp.*` properties. **Do not copy a per-app `HostRegistrar`** — auto-configuration starts the attach loop on `ApplicationReadyEvent` and stops it on shutdown (no deregister).

**Dependency:**

```xml
<dependency>
  <groupId>org.example</groupId>
  <artifactId>aipp-protocol-spring</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

**`application.yml` (recommended):**

```yaml
spring:
  application:
    name: note-one

aipp:
  app-id: note-one                    # optional; defaults to spring.application.name
  host:
    base-url: http://127.0.0.1:8090   # required — enables auto-configuration
  self-base-url: http://localhost:${server.port}
  attach:
    enabled: true                     # default true; set false in tests to skip
```

Auto-configuration class: `org.twelve.aipp.host.spring.AippHostAttachAutoConfiguration`.

### Configuration keys

| Key | Meaning | Default |
|-----|---------|---------|
| `aipp.app-id` | kebab-case id, matches `/api/app.app_id` | `spring.application.name` |
| `aipp.host.base-url` | Host (world-one) URL | *(required to enable attach)* |
| `aipp.self-base-url` | address the Host uses to reach this app | *(required)* |
| `aipp.attach.enabled` | start attach loop on boot | `true` |
| `aipp.attach.initial-attempts` | retries while Host is down at boot | `30` |
| `aipp.attach.retry-delay` | delay between boot retries | `5s` |
| `aipp.attach.refresh-interval` | periodic re-attach interval | `15s` |

Environment aliases commonly used alongside `aipp.host.base-url`:

- `AIPP_HOST_BASE_URL` → `WORLD_ONE_BASE_URL` → `http://127.0.0.1:8090`

---

## Related

- [`host-registration.md`](host-registration.md) — what the Host pulls at install; manual install.
- [`host-url.md`](host-url.md) — resolving the Host base URL inside app code.
- [`host-injection.md`](host-injection.md) — `PUT /api/host/bindings` after install.
- world-one `RegistryController` — `POST /api/registry/install` (idempotent attach),
  `DELETE /api/registry/{appId}` (optional deregister).
