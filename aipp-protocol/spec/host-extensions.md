# Declarative Host extensions (`GET /api/app.host_extensions`)

> **Executable contract:** `org.twelve.aipp.host.AippHostExtensionSpec`.
> If this document disagrees with an `assert*` method, the Java method wins.

An independent AIPP may contribute actions to stable Host shell slots and may
implement a versioned public interface from `shared`. The Host aggregates valid
contributions through `GET /api/host/extensions`; it does not contain an app id,
tool name, module path, or domain-specific branch for any provider.

## Manifest shape

```json
{
  "host_extensions": {
    "schema_version": 1,
    "banner_icons": [{
      "operation": "register_banner_icon",
      "id": "library",
      "label": {"en": "Library", "zh": "资源库"},
      "icon": "app",
      "action": {"kind": "tool", "tool": "library_open"},
      "order": 100
    }],
    "banner_tabs": [{
      "operation": "register_banner_tab",
      "id": "details",
      "label": {"en": "Details"},
      "action": {"kind": "tool", "tool": "details_open"},
      "order": 100
    }],
    "interface_providers": [{
      "type": "shared.example.apply/v1",
      "module": "/runtime/example-interface.js",
      "bootstrap_tool": "example_current",
      "probe_interval_ms": 30000
    }]
  }
}
```

All three arrays are required and may be empty. Each array has at most eight
items. Labels are localized strings with a required English value.

## Shell contribution rules

- `register_banner_icon` targets the Host top banner; `icon: "app"` uses the
  contributing app's validated/sanitized manifest icon.
- `register_banner_tab` targets the Host right banner.
- Actions are declarative tool calls only. URLs and JavaScript actions are not
  protocol fields and must be rejected.
- The Host supplies `app_id`, online state, app icon, and app color from its
  registry, sorts by `order`, and invokes the action through its generic app/tool
  routing path.
- A contribution is visible only while its owning AIPP is registered. An offline
  contribution may remain rendered as disabled until liveness recovers.

## Shared interface provider rules

- `type` names a public, versioned interface whose protocol lives in `shared`.
- `module` is a safe absolute app-local `.js` path. The Host rewrites it to the
  owning app's proxy path; providers cannot name another app or a remote origin.
- `bootstrap_tool` returns the provider's current declarative effect and any
  owner-supplied fallback required by that public interface.
- Duplicate providers for one interface type fail closed: the Host publishes a
  conflict and publishes no implementation for that type.
- The shared interface registry may cache an opaque provider declaration and
  owner-provided fallback for availability. It must not duplicate domain defaults
  or interpret domain payloads in Host code.

## Trust boundary

The Host validates the manifest before indexing it. Shell rendering uses text
nodes and sanitized icons. Interface module URLs are restricted to the declaring
AIPP's proxy. Interface-specific validation and behavior belong to the public
shared interface and its AIPP implementation, never to the Host shell.

