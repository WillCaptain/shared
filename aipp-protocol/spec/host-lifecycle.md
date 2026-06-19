# AIPP ↔ Host Lifecycle (register / deregister / liveness)

> **Discovery:** [`INDEX.md`](INDEX.md) → this file. Pairs with [`host-registration.md`](host-registration.md)
> (the install contract) and [`host-url.md`](host-url.md) (how the AIPP learns the Host URL).
> **Helper:** `org.twelve.aipp.host.AippHostLifecycle` (framework-agnostic, in aipp-protocol).

A **standard AIPP manages its own presence in the Host app list**: it registers on launch,
deregisters on shutdown, and relies on the Host's liveness probe to reflect crashes. No manual
`~/.ones/apps/{app_id}/manifest.json` editing is required.

---

## 1. Three behaviors

| When | Who acts | Call | Effect |
|------|----------|------|--------|
| **Launch** | AIPP → Host | `POST {host}/api/registry/install` `{app_id, base_url}` | App appears in the app list |
| **Shutdown** | AIPP → Host | `DELETE {host}/api/registry/{app_id}` | App is removed (logout) |
| **App list opened** | Host → AIPP | `GET {base_url}/api/tools` (throttled probe) | Liveness / `is_active` reflects reachability |

`base_url` is the **externally reachable address the Host uses to reach the AIPP** — not the Host URL.

---

## 2. Register on launch

On startup the AIPP POSTs its identity to the Host. Because the Host may start *after* the AIPP,
registration must **retry** while the Host is unreachable, and must run **off the main thread** so
it never blocks app boot. It is best-effort: failure must not crash the app.

```bash
curl -X POST http://localhost:8090/api/registry/install \
  -H 'Content-Type: application/json' \
  -d '{"app_id":"note-one","base_url":"http://localhost:8096"}'
# → {"success":true,"message":"App note-one installed successfully"}
```

The Host then pulls `/api/app`, `/api/tools`, `/api/skills`, `/api/widgets` and persists a manifest
(see [`host-registration.md`](host-registration.md) §1), then calls `PUT /api/host/bindings`.

## 3. Deregister on shutdown

On graceful shutdown (e.g. SIGTERM → Spring `@PreDestroy`) the AIPP deletes its registration so it
disappears from the app list immediately rather than lingering as an offline entry.

```bash
curl -X DELETE http://localhost:8090/api/registry/note-one
# → {"success":true,"message":"App note-one deregistered"}
```

The Host endpoint is **idempotent**: deregistering an unknown app still returns success, so a
double shutdown or an already-evicted app produces no error. Deregistration purges the app from the
live registry (tools, skills, widgets, routing indexes) and deletes the persisted manifest.

> A hard crash (kill -9, OOM) cannot deregister. That case is covered by the liveness probe (§4):
> the Host marks the app offline on the next app-list open.

## 4. Liveness probe (Host-side)

When the app list is opened, the Host probes each non-builtin app's `GET /api/tools` (throttled, run
asynchronously so the list isn't blocked on I/O) and records the result as the app's online status.
An unreachable app is shown as inactive/offline rather than removed. **AIPPs need no client-side
heartbeat** — just keep the core endpoints fast and side-effect-free on GET.

---

## 5. Reference helper

`AippHostLifecycle` (pure `java.net.http`; wire it from your framework's startup/shutdown hooks):

```java
var lifecycle = new AippHostLifecycle(hostBaseUrl, appId, selfBaseUrl);

// startup (run on a daemon thread)
lifecycle.registerWithRetry(/*maxAttempts*/ 30, Duration.ofSeconds(5));

// shutdown
lifecycle.deregister();
```

Spring example: call `registerWithRetry` from an `ApplicationReadyEvent` listener on a daemon
thread, and `deregister` from a `@PreDestroy` method. See `ones/note-one` `HostRegistrar` for a
working reference.

### Configuration keys (recommended)

| Key | Meaning | Default |
|-----|---------|---------|
| `{app}.app-id` | kebab-case id, matches `/api/app.app_id` | the app id |
| `{app}.host.base-url` | Host (world-one) URL | `AIPP_HOST_BASE_URL` → `WORLD_ONE_BASE_URL` → `http://127.0.0.1:8090` |
| `{app}.self-base-url` | address the Host uses to reach this app | `http://localhost:${server.port}` |

---

## Related

- [`host-registration.md`](host-registration.md) — what the Host pulls at install; manual install.
- [`host-url.md`](host-url.md) — resolving the Host base URL inside app code.
- [`host-injection.md`](host-injection.md) — `PUT /api/host/bindings` after install.
- world-one `RegistryController` — `POST /api/registry/install`, `DELETE /api/registry/{appId}`.
