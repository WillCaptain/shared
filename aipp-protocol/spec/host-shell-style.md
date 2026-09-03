# Shared theme interface — shell projection and sandbox runtime

> **Audience:** shared-interface authors, Theme One implementers, and Host shell implementers.
> **Domain owner:** `theme-one` AIPP.
> **Host responsibility:** provide only the generic AIPP proxy, generic host-effect dispatch,
> a stable shell mount, and shared interface resources.

## 1. Ownership boundary

Theme One owns all theme-domain behavior:

- built-in package discovery from Theme One's configured resource directory;
- per-user library and `visible` / `hidden` status (`visible` by default);
- project authoring and deterministic compilation;
- package validation, installation, assets, active selection, and uninstall;
- the trusted token projection and animation-IR implementation served by the AIPP.

`shared/theme` is an optional release-authoring and package-generation workspace;
it is not a runtime catalog. `shared/host-interfaces` owns the neutral browser-side
public-interface registry. `shared/aipp-protocol` owns package and Host-extension
validators and the public contracts. Host chrome CSS lives in
`ones/world-one/src/main/resources/static/css/`. `shared/css` retains theme
generator catalogs (`themes/`, `*-presets.json`) for Once sync only — not a
user-theme registry or Host chrome source.

World One does not own theme persistence, catalogs, tools, endpoints, package
storage, package validation, a Style panel, or a theme-specific effect branch.
Its generic app proxy may attach authenticated interface headers and its generic
effect dispatcher may call a registered shared interface.

## 2. Package distribution

Every theme is a self-contained `.ones-theme` v1 archive defined by
[`theme-packages.md`](theme-packages.md). Built-in and user archives have the same
layout and validator. Their only difference is distribution:

- built-in package folders ship under Theme One's configured external
  `resources/themes/` directory;
- user archives are installed under Theme One's per-user runtime directory.

Compiling or installing a user package must not modify shared CSS, built-in
catalogs or sources, any Host source, or any source-controlled theme file.

## 3. Shared browser interface

The registered effect type is `shared.theme.apply/v1`:

```json
{
  "type": "shared.theme.apply/v1",
  "payload": {
    "schema_version": 1,
    "package_id": "user.alice.night-garden",
    "version": "1.0.0",
    "instance_id": "24-lowercase-hex-chars",
    "tokens": {},
    "shell": {},
    "animation": { "program": {}, "fallback": {} },
    "assets": { "background": null, "icon": null }
  }
}
```

Theme One declares its implementation with `GET /api/app.host_extensions` as
defined by [`host-extensions.md`](host-extensions.md). The Host validates and
aggregates that declaration without knowing Theme One's app id, then rewrites its
app-local module path to the owning app proxy. Effects cannot supply a module URL.
On startup the shared registry discovers the provider through the generic Host
directory, calls its declared bootstrap tool through the generic tool proxy, and
dispatches the returned effect.

`theme_current` returns `host_effect` for the user's current package. A transient
probe timeout preserves the last validated active projection. After repeated
consecutive failures confirm that Theme One is unavailable, the registry unloads
the projection and exposes the Host's one neutral built-in CSS set. It does not
substitute Ones Light, Ones Dark, or any other named package as a fallback.

The Theme One module validates the descriptor again in the browser, unloads the
previous instance, and applies only typed values. Generated token CSS is scoped
to the exact `data-ones-theme-package-instance` value and mounted widget roots.
Validated `theme/style.css` is inserted only after replacing its required
`:theme` prefix with that exact package-instance selector. Effects CSS remains
isolated from the Host document in its sandbox.

## 4. Shell layers

The stable shell exposes one pointer-transparent background root containing
exactly three ordered visual layers:

| Order | Layer | Projection |
|---|---|---|
| 1 | background color | neutral `--aipp-bg` token |
| 2 | background image | package-local sanitized asset URL |
| 3 | background animation | trusted interpreter in the root's single animation mount |

Tokens, chrome, icons, and component styling are projections, not additional
background layers. Theme packages may contain multiple declarative animation IR
layers, but all of them render into the one Host animation mount.

Theme switches must remove the previous scoped stylesheet, effects iframe,
background/icon state, and animation program before attaching the new instance.

## 5. Security rules

- Package paths are package-local and pass `ThemePackageSpec` validation.
- Runtime assets are served only by Theme One's exact owner/package/version
  endpoint; remote and caller-provided URLs are rejected.
- Tokens and shell values are declarative and allow-listed. They never become
  unparsed global CSS.
- Optional `theme/effects.css` runs only in a scriptless opaque-origin iframe
  with a network-denying CSP.
- Animation files are declarative JSON, never package JavaScript. The interpreter
  runs in `<iframe sandbox="allow-scripts">` without same-origin, network,
  storage, parent DOM, popup, form, or worker authority.
- The fallback program is required. `prefers-reduced-motion: reduce` or package
  motion policy selects it; motion `off` stops animation entirely.
- Descriptor and package validation failures fail closed and leave the current
  theme untouched.

## 6. Discovery

Theme One publishes router-promoted tools including `theme_library_list`,
`theme_current`, and `theme_apply`, plus the `manage_themes` and `build_theme`
skills. The capability tree can therefore discover theme operations in any
session without a Host built-in `get_theme` or `set_theme` tool.

Theme One also publishes a declarative `register_banner_icon` contribution whose
tool action opens its theme-selection widget. The icon follows the current shell
theme icon. World One renders all registered icons through the same generic shell
slot and contains no Theme One selector markup or click branch.

## 7. Compliance checklist

- [ ] User themes compile into canonical deterministic `.ones-theme` archives.
- [ ] Compiler output passes the independent installation validator.
- [ ] Built-in and user packages use the same validator and descriptor shape.
- [ ] New library items are `visible`; hidden items are omitted from listing.
- [ ] Two user packages can install and switch independently.
- [ ] Switching unloads all previous style, asset, effects, and animation state.
- [ ] Reduced-motion selects the fallback program.
- [ ] Unsafe CSS, paths, URLs, executable files, and unsupported capabilities fail closed.
- [ ] World One contains no theme tools, settings persistence, package endpoints,
      package runtime, or theme-management panel.
- [ ] Theme One owns the only canonical default; the Host stores at most an opaque runtime cache.
- [ ] Host boot does not wait for Theme One, and provider recovery restores the current package.
