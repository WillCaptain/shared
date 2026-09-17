# HTTP operation routes v1

Status: implementation groundwork for editor API compatibility. Declaration and matching do not
authenticate requests, enable app endpoints or replace consumer authorization. Do not enable a
protected app until its Host dispatch and consumer integration tests pass.

An optional tool-level field maps extra app-local HTTP routes to that tool's operation:

```json
{
  "name": "document_rename",
  "http_routes": {
    "version": "operation-routes-v1",
    "routes": [{"method": "PATCH", "path": "/api/documents/{id}/rename"}]
  }
}
```

The operation remains a real registered tool, with its ordinary tool endpoint and unchanged
visibility, retry-safety, function-authority and identity declarations. The mapping must come from
the installed app catalog, never request arguments. An alias is not an unprotected fallback.

## Matching and bounds

- Methods are exact uppercase GET, HEAD, POST, PUT, PATCH or DELETE. HEAD is not inferred from GET.
- Paths begin `/api/`; the first two segments are literal. `/api/tools/*` and `/api/proxy/*` are
  reserved and cannot be declared as aliases. Other segments may be `{name}` single-segment slots.
- Literal and requested segments use ASCII letters, digits, underscore, hyphen and non-leading dots,
  at most 128 characters. Slot names are unique and use letters/digits/underscore, starting with a letter.
- No origins, regexes, wildcards, percent-encoded paths, empty segments, trailing slash, matrix
  parameters, dot segments, fragments or query conditions in declarations. Invalid inputs fail closed.
- A route path is at most 1024 characters and 16 segments; a complete request target at most 4096
  ASCII characters. Queries may be percent-encoded and are forwarded/bound byte-for-byte, not decoded
  or normalized by the matcher. Query validation and resource scope remain consumer-owned.
- At most 32 aliases per tool, 256 aliases and 4096 tools per compiled app catalog.
- Duplicate names/routes and any overlapping same-method patterns invalidate the catalog, even
  when a literal looks more specific than a slot. There is no first-match precedence rule.

`HttpOperationRoutes.compile` creates an immutable per-app snapshot; `resolve` returns an operation
name and identity requirement, not a permission grant. `AippAppSpec` validates individual entries
and cross-operation collisions when the tools manifest declares aliases. Existing manifests without
the field are unaffected. Snapshot caching must be invalidated on catalog change; no stale fallback.

## Host and consumer obligations

Before forwarding, the Host must resolve exactly one operation, enforce its function gate and
recheck current installed metadata. If `invocation_identity` is declared, use verified request
evidence for the exact method, raw target/query and body. The consumer verifies evidence independently
and enforces its own resource permissions. Unknown requirements and missing trust fail closed.

Never forward login bearers or caller identity evidence to implement aliases. Reject unsupported
streaming/content types rather than converting the method or body. No automatic mutation retry.
Unmapped routes of protected apps remain blocked; declared static widget assets have a separate
read-only profile. A Host must not infer legacy exemptions from endpoint names or `.js` suffixes.
