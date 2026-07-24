# Deploy & Host attach (index)

> Thin index. Normative: [`../../../spec/host-lifecycle.md`](../../../spec/host-lifecycle.md), [`../../../spec/host-registration.md`](../../../spec/host-registration.md).
> Checklist: [`../../../docs/quickstart-checklist.md`](../../../docs/quickstart-checklist.md) §8.

## Default path (recommended)

1. Depend on **`aipp-protocol-spring`** (or call `AippHostLifecycle` yourself).
2. Set `aipp.host.base-url` (Host) and `aipp.self-base-url` (this AIPP’s reachable URL).
3. On launch the AIPP **attach loop** POSTs `POST {host}/api/registry/install` with `{app_id, base_url, instance_id}` (~every 15s).
4. Host keeps AIPPs **in memory only** (online registry): probes `GET {base_url}/api/tools` for liveness. After Host restart, each AIPP must re-attach — there is no on-disk AIPP catalog.
5. Implement `PUT /api/host/bindings` when the Host injects env/callbacks — [`host-injection.md`](../../../spec/host-injection.md).

## Manual / smoke (dev)

```bash
curl -X POST http://localhost:8090/api/registry/install \
  -H 'Content-Type: application/json' \
  -d '{"app_id":"<app-id>","base_url":"http://localhost:<port>","instance_id":"<uuid>"}'
```

Then verify:

| Check | Endpoint |
|-------|----------|
| Listed | `GET {host}/api/registry` |
| Tree (if used) | `GET {host}/api/capability-trees/{app_id}` |
| Chat smoke | `POST {host}/api/chat` triggers at least one tool |

## Done criteria

- [ ] Four core endpoints up
- [ ] `assert*` / app compliance tests green (`spec/verify.md`)
- [ ] Attach succeeds (or manual install listed in registry)
- [ ] Exactly one `is_main` widget; `app_id` consistent
