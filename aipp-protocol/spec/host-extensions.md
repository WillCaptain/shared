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
      "icon": "shell",
      "action": {"kind": "app_main"},
      "order": 100
    }],
    "banner_tabs": [{
      "operation": "register_banner_tab",
      "id": "details",
      "label": {"en": "Details"},
      "action": {"kind": "panel", "module": "/right-panel/details.js"},
      "badge": {"kind": "count", "module": "/right-panel/details-badge.js",
                "background_agent_turn": false},
      "order": 100
    }],
    "interface_providers": [{
      "type": "shared.example.apply/v1",
      "module": "/runtime/example-interface.js",
      "bootstrap_tool": "example_current",
      "probe_interval_ms": 30000
    }],
    "attachment_sources": [{
      "operation": "register_attachment_source",
      "id": "library",
      "label": {"en": "12th Lib", "zh": "12斋"},
      "icon": "library",
      "module": "/attachment-source/library.js",
      "multiple": true,
      "order": 100
    }]
  }
}
```

The three legacy arrays are required and may be empty. `attachment_sources` is
an additive optional v1 field so existing manifests remain valid. Each array has
at most eight items. Labels are localized strings with a required English value.

## Composer attachment-source rules

- `register_attachment_source` contributes an item to the Host-owned composer
  `+` menu. Finder remains a built-in final item and cannot be removed.
- With no available AIPP sources, the Host opens Finder directly. With one or
  more sources, it shows the ordered source menu before opening a picker.
- Identity is `(app_id, id)`. Multiple AIPPs may contribute sources; this is not
  a singleton shared-interface provider.
- `module` is a safe app-local `.js` path rewritten through the declaring app's
  proxy. It exports `open(context)` and returns a `File[]`, `FileList`, a
  `{files}` wrapper, or `null` when cancelled.
- `context` includes `appId`, `extensionId`, `language`, `area`, `multiple`,
  `accept`, `runtime`, `appProxyUrl(path)`, and `proxyTool(name,args,options)`.
- `icon` is `app`, `file`, or `library`; `multiple` declares whether the picker
  permits more than one selection. The Host validates results before adding
  them to the composer.
- Provider modules are imported only after the user selects their menu item.
  Offline contributions are omitted from the menu.

## Shell contribution rules

- `register_banner_icon` targets the Host top banner. `icon: "app"` uses the
  contributing app's validated/sanitized manifest icon; `icon: "shell"` follows
  the current icon projected by a shared shell interface.
- `register_banner_tab` targets the Host right banner.
- `action.kind: "app_main"` opens the declaring AIPP through its canonical
  `main_widget_type` and widget `entry_tool`. `action.kind: "tool"` invokes the
  explicitly named tool. On a banner tab, `action.kind: "panel"` mounts the
  declaring app's ESM `module` into the Host-owned right panel. The module exports
  `mount(target, context)` and may return a cleanup function. Remote URLs and
  inline JavaScript actions are not protocol fields and must be rejected.
- A panel `module` is a safe absolute app-local `.js` path. The Host rewrites it
  to the declaring app's proxy URL before exposing the extension directory.
- A banner tab may declare an app-owned numeric badge provider as
  `badge: {"kind":"count","module":"/...js"}`. The provider module exports
  `subscribe(context, publish)`, calls `publish(nonNegativeInteger)` whenever its
  count changes, and returns an optional cleanup function. The Host displays zero
  as no badge, `1..99` as the number, and larger values as `99+`. The AIPP owns
  the count's meaning, persistence, refresh strategy, and read/dismiss lifecycle;
  the Host must not poll or interpret business data.
- A badge provider that must process owner-authorized background work may explicitly
  declare `background_agent_turn: true`. Only then does its context include
  `runAgentTurn(spec)`. The Host binds the turn to the declaring AIPP's source session;
  ordinary count providers never receive this capability. This field grants access to
  the Host agent, not permission to use tools, disclose resources, or bypass the AIPP's
  own user policy.
- Badge modules are loaded while their AIPP is registered, independently of the
  panel being open. Their `context` provides `appId`, `extensionId`, `language`,
  `appProxyUrl(path)`, and `proxyTool(name,args,options)`. When explicitly declared,
  the context additionally provides `runAgentTurn(spec)`. Remote modules and
  cross-app paths are rejected and rewritten through the declaring app's proxy.
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
