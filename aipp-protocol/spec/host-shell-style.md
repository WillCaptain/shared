# Shared theme interface — shell projection and sandbox runtime

> **Audience:** shared-interface authors, Theme One implementers, and Host shell implementers.
> **Domain owner:** `theme-one` AIPP.
> **Host responsibility:** provide only the generic AIPP proxy, generic host-effect dispatch,
> a stable shell mount, and shared interface resources.

## 1. Ownership boundary

Theme One owns all theme-domain behavior:

- factory package discovery;
- per-user library and `visible` / `hidden` status (`visible` by default);
- project authoring and deterministic compilation;
- package validation, installation, assets, active selection, and uninstall;
- the trusted token projection and animation-IR implementation served by the AIPP.

`shared/theme` owns package compiler infrastructure, factory sources, and browser
interface registration. `shared/aipp-protocol` owns the Java package validator
and this protocol. `shared/css` contains only stable component and theme-engine
CSS. It does not contain a user-theme registry or one generated stylesheet per
user theme.

World One does not own theme persistence, catalogs, tools, endpoints, package
storage, package validation, a Style panel, or a theme-specific effect branch.
Its generic app proxy may attach authenticated interface headers and its generic
effect dispatcher may call a registered shared interface.

## 2. Package distribution

Every theme is a self-contained `.ones-theme` v1 archive defined by
[`theme-packages.md`](theme-packages.md). Factory and user archives have the same
layout and validator. Their only difference is distribution:

- factory archives ship as Theme One classpath resources generated from
  `shared/theme` factory sources;
- user archives are installed under Theme One's per-user runtime directory.

Compiling or installing a user package must not modify shared CSS, shared factory
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

The shared registry has an allow-listed mapping from the effect type to Theme
One's module path. Effects cannot supply an arbitrary module URL. On startup the
registry calls Theme One's router-promoted `theme_current` tool through the
generic tool proxy, then dispatches the returned effect.

`theme_current` returns both `host_effect` (the user's current selection) and
`fallback_effect` (Theme One's canonical default). The shared registry asks the
Theme One interface implementation to prepare the fallback, then stores only
the opaque effect envelope and package-local asset responses in browser caches.
The Host does not define, copy, or interpret the default theme. If Theme One
becomes unreachable, the registry applies that owner-supplied cached fallback;
when it recovers, the next probe restores the current selection. A first-ever
cold start with no prepared fallback remains on the neutral stable shell.

The Theme One module validates the descriptor again in the browser, unloads the
previous instance, and applies only typed values. Generated token CSS is scoped
to the exact `data-ones-theme-package-instance` value and mounted widget roots.
Package CSS is never inserted into the Host document.

## 4. Shell layers

The stable shell exposes three pointer-transparent layers:

| Order | Layer | Projection |
|---|---|---|
| 1 | tokens and shell effects | validated `--aipp-*` variables and capability attributes |
| 2 | background asset | package-local sanitized asset URL |
| 3 | background animation | trusted interpreter in a sandboxed canvas iframe |

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

## 7. Compliance checklist

- [ ] User themes compile into canonical deterministic `.ones-theme` archives.
- [ ] Compiler output passes the independent installation validator.
- [ ] Factory and user packages use the same loader and descriptor shape.
- [ ] New library items are `visible`; hidden items are omitted from listing.
- [ ] Two user packages can install and switch independently.
- [ ] Switching unloads all previous style, asset, effects, and animation state.
- [ ] Reduced-motion selects the fallback program.
- [ ] Unsafe CSS, paths, URLs, executable files, and unsupported capabilities fail closed.
- [ ] World One contains no theme tools, settings persistence, package endpoints,
      package runtime, or theme-management panel.
- [ ] Theme One owns the only canonical default; the Host stores at most an opaque runtime cache.
- [ ] Host boot does not wait for Theme One, and provider recovery restores the current package.
