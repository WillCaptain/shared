# Host Registration & Smoke Test

> **Discovery:** [`AGENTS.md`](../AGENTS.md) → [`quickstart-checklist.md`](../docs/quickstart-checklist.md) §8.  
> **Compliance before register:** [`verify.md`](verify.md).

---

## 1. What Host pulls at install

On register/reload, Host (world-one) fetches from your `base_url`:

| Endpoint | Purpose |
|----------|---------|
| `GET /api/app` | Identity, optional `configuration.ui` |
| `GET /api/tools` | Tool catalog + decoupling fields |
| `GET /api/skills` | Skill index (may be empty) |
| `GET /api/widgets` | Widget manifests |

Then Host calls `PUT /api/host/bindings` on your app with `env`, `host_base_url`, etc.

Your app must be **reachable** from Host at the URL you register.

---

## 2. Register on world-one

### API (dynamic, no Host restart)

```bash
curl -X POST http://localhost:8090/api/registry/install \
  -H 'Content-Type: application/json' \
  -d '{"app_id":"recipe-one","base_url":"http://localhost:9000"}'
```

Success: `{"success":true,"message":"App recipe-one installed successfully"}`  
Failure: `{"success":false,"error":"..."}` — usually unreachable base URL or invalid manifest (check Host logs).

> **Standard apps self-register / self-deregister** — don't call install by hand for production
> apps. Implement the launch/shutdown lifecycle from [`host-lifecycle.md`](host-lifecycle.md)
> (helper: `AippHostLifecycle`). The manual install above is for ad-hoc/dev use.

### Deregister

```bash
curl -X DELETE http://localhost:8090/api/registry/recipe-one
# → {"success":true,"message":"App recipe-one deregistered"}
```

Idempotent — removing an unknown app still returns success. See [`host-lifecycle.md`](host-lifecycle.md).

### List registered apps

```bash
curl -s http://localhost:8090/api/registry | jq .
```

### Filesystem manifest (persistent deploy)

Some deployments use `~/.ones/apps/{app_id}/manifest.json`:

```json
{ "id": "recipe-one", "api": { "base_url": "http://localhost:9000" } }
```

Exact path depends on Host deployment — API install is the portable contract.

---

## 3. Post-install checks

```bash
# Merged widget catalog (includes worldone-system sys.*)
curl -s http://localhost:8090/api/widgets | jq '[.widgets[]|select(.app_id=="recipe-one")]|length'

# Capability tree (if used)
curl -s http://localhost:8090/api/capability-trees/recipe-one | jq '.app_id'

# Aggregated tools visible to LLM
curl -s http://localhost:8090/api/registry/tools | jq '.tools|length'
```

Chat smoke:

```bash
curl -X POST http://localhost:8090/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"list recipes","session_id":"smoke-test"}'
```

---

## 4. Common install failures

| Error symptom | Likely cause |
|---------------|--------------|
| Install returns `success: false` | Wrong `base_url`, firewall, app not running |
| App missing from router | `allowed_tools` on skill references unknown tool → skill dropped |
| Tool rejected at load | Tool entry has `prompt` / `tools[]` / `resources` (forbidden) |
| No widget UI | Missing `render.url`, or `is_main` not exactly one |
| `sys.*` in widgets | Register rejected — use tool responses for `sys.*` |

---

## 5. Development workflow

1. Implement endpoints locally.
2. Run [`verify.md`](verify.md) fixtures in CI.
3. `POST /api/registry/install` against dev Host.
4. Smoke chat + one canvas open.
5. Iterate — reinstall after manifest changes (or Host hot-reload if supported).

---

## Related

- Capability tree on Host: [`capability-tree.md`](capability-tree.md)
- Host bindings: [`host-injection.md`](host-injection.md)
- world-one `RegistryController` — `POST /api/registry/install`
