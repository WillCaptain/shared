# Effect identity

> **Discovery:** [`INDEX.md`](INDEX.md) → this file.
> **Verify:** `AippAppSpec.assertValidEffectIdentityField` (auto-run by `assertValidToolsApiStructure`).
> **Java:** `org.twelve.aipp.host.AippEffectIdentityContract`.

An AIPP may ask Host to skip a later identical completed client effect in the same chat. Host does not know what the effect means. The owning AIPP computes an opaque identity; Host only stores and replays it.

This is **not** tool execution. `POST /api/tools/{name}` on a client-only tool remains forbidden ([`client-execution.md`](client-execution.md) INV-3). Classify is a sibling protocol path.

## Catalog flag

Optional boolean on a `GET /api/tools` entry:

```json
{ "name": "example_write", "execution_surface": "client", "effect_identity": true }
```

| Rule | Meaning |
|------|---------|
| Absent or `false` | Host always dispatches. No classify call. |
| `true` | Tool must include a client `execution_surface`. Before client dispatch, Host POSTs classify to the owning app. |
| Other types | Invalid manifest (`assertValidEffectIdentityField`). |

Host must not treat any other catalog field (`desktop_effect`, operator tables, OS families, tool names) as write-identity input.

## Classify request

```
POST {app.baseUrl}/api/tools/{name}/effect-identity
Content-Type: application/json

{ "args": { ...tool arguments... }, "platform": "mac" }
```

`platform` is the connected executor’s platform label when Host has one; it may be blank. `args` is the same map Host would send to the client executor.

Path construction is `AippEffectIdentityContract.path(name)` only. Apps must not advertise a different classify URL. This path is protocol-reserved; it is not an `http_routes` alias ([`http-operation-routes.md`](http-operation-routes.md)).

## Classify response

```json
{ "ok": true, "identity": "opaque-or-null" }
```

| `identity` | Host |
|------------|------|
| omitted, JSON `null`, or blank | Always dispatch this call. |
| non-blank string | Opaque key for this chat. If a prior call with the same key completed as success, Host returns the stored result with `status=replayed` and `error_class=effect_already_completed` and does not dispatch. |

Host must not parse the string. Failed or uncertain first results are not stored. Classify HTTP failure, timeout, or malformed JSON is fail-open: dispatch.

Replay JSON (Host-owned envelope over the prior successful payload):

```json
{ "ok": true, "status": "replayed", "error_class": "effect_already_completed" }
```

`error` is removed so outcome classification stays success. Extra prior fields may be retained.

## Ownership

| Layer | Owns | Must not |
|-------|------|----------|
| AIPP private convention / code | How identity is computed (operators, paths, hashes) | Host Java, Once |
| Catalog `effect_identity: true` | Opt-in to classify | Shell tokens on `GET /api/tools` for Host to interpret |
| Host | Boolean flag, classify POST, opaque store, replay error class | `>>`, cmdlets, posix/windows families, provider tool names |
| Once | Execute whatever Host dispatches | Identity |

Operators belong in the AIPP that executes the desktop effect. Host stays contract and error-class only.
