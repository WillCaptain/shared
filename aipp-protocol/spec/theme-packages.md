# Ones theme package v1

> **Status:** normative
> **Container:** ZIP with extension `.ones-theme`
> **MIME:** `application/vnd.ones.theme+zip`
> **Runtime owner:** the Host

This specification defines the only package format accepted for user-installable
Host shell themes. A package is untrusted input even when produced by
`ones-theme-builder` or signed by Ones Market.

## 1. Security boundary

- A package contains declarative JSON, raster images, and optionally one bounded
  `theme/effects.css` visual-effects stylesheet.
- JavaScript, HTML, SVG, WebAssembly, fonts, audio, video, native code,
  symlinks, devices, and nested archives are forbidden.
- Effects CSS runs only in a scriptless opaque-origin iframe with a
  network-denying CSP. It is never inserted into the Host document.
- Theme Builder compiles projects; the Host independently validates every byte
  before preview, installation, or asset serving.
- A signature proves provenance and integrity. It never bypasses validation.
- ZIP bytes use the staged `upload_ref`/`artifact_ref` channel defined in §9.
  They must not be placed in tool arguments, widget data, or LLM context.

## 2. Canonical package layout

```text
theme.ones-theme
├── manifest.json
├── theme/
│   ├── tokens.json
│   ├── shell.json
│   └── effects.css                 # optional, sandbox-only
├── animation/
│   ├── program.json
│   └── fallback.json
├── background/background.webp       # optional when background.kind = none
├── icon/icon.webp                    # optional when icon.kind = host_default
├── previews/card.webp                # optional
├── previews/hero.webp                # optional
├── source/project.json               # optional, never executed
├── LICENSE.txt
├── integrity.json
└── signature.ed25519                 # market packages only
```

This nested layout is canonical for both factory and user-built v1 packages.
Earlier design sketches that used root-level `tokens.json`, `tokens.css`,
`shell.css`, `assets/*`, or `programs/*` are not v1-compatible. In particular,
`tokens.css` and `shell.css` remain forbidden: the Host projects validated
`theme/tokens.json` and `theme/shell.json` values into instance-scoped CSS
variables. The optional, validator-constrained `theme/effects.css` capability
is never attached to the Host document; it may run only inside the separate,
scriptless effects sandbox described below.

Factory and user packages use the same inventory and validator. Distribution
is the only distinction: factory archives ship with Ones, while user archives
are installed outside the static/source tree and loaded on demand.

ZIP generation is deterministic:

1. Paths are UTF-8, `/` separated, normalized, unique, and lexicographically
   ordered.
2. Entry timestamps are `1980-01-01T00:00:00Z`.
3. JSON is UTF-8, uses sorted object keys, no insignificant whitespace, and one
   trailing newline.
4. Platform-specific owner, permission, and extra metadata are omitted.
5. `signature.ed25519` is not listed in `integrity.json`; every other regular
   file is listed.

Directories may be implicit. Empty files and undeclared files are rejected.

## 3. Limits

The following v1 limits are normative and are exposed by
`ThemePackageSpec.Limits.defaults()`:

| Item | Limit |
|---|---:|
| Compressed package | 20 MiB |
| Total expanded bytes | 60 MiB |
| ZIP entries | 200 |
| Single-entry compression ratio | 50:1 |
| JSON document | 256 KiB |
| JSON nesting depth | 32 |
| Raster assets | 12 |
| Total decoded raster pixels | 32,000,000 |
| Single raster dimension | 8,192 px |
| Animation layers | 32 |
| Animation nodes | 512 |
| Animation node depth | 16 |
| Active particles | 600 |

Quarantine must enforce the compressed limit while streaming. Validation must
enforce expanded size before extraction to a persistent location.

## 4. `manifest.json`

```json
{
  "schema_version": 1,
  "package_id": "ones.standard.hatsune-miku",
  "version": "1.0.0",
  "name": { "en": "Hatsune Miku", "zh": "初音未来" },
  "description": {
    "en": "A turquoise virtual-stage theme.",
    "zh": "苍绿色虚拟舞台主题。"
  },
  "publisher": {
    "id": "ones",
    "display_name": "Ones"
  },
  "min_host_version": "1.0.0",
  "components": {
    "tokens": "theme/tokens.json",
    "shell": "theme/shell.json",
    "background": "background/background.webp",
    "animation": "animation/program.json",
    "animation_fallback": "animation/fallback.json",
    "icon": "icon/icon.webp",
    "effects": "theme/effects.css"
  },
  "capabilities": {
    "pointer": true,
    "local_time": false,
    "reduced_motion": true
  },
  "license": "LICENSE.txt",
  "integrity": "integrity.json"
}
```

Rules:

- Unknown top-level and nested fields are rejected.
- `package_id` matches
  `[a-z0-9](?:[a-z0-9.-]{1,126}[a-z0-9])?` and is immutable.
- `version` and `min_host_version` are SemVer without a leading `v`.
- User-facing localized strings require a non-blank `en`; other locale keys use
  BCP-47-like lower-case tags.
- The six core component keys are required; `effects` is optional. `background`
  may be `null` only when
  `shell.json` declares `background.kind: "none"`. `icon` may be `null` only
  when `shell.json` declares `icon.kind: "host_default"`.
- All non-null component, license, and integrity paths are safe package-local
  paths and must resolve to declared regular files.

## 5. Theme documents

### 5.1 `theme/tokens.json`

`schema_version` is `1`. The required semantic color keys are:

`bg`, `surface`, `surface2`, `surface3`, `text`, `textDim`, `textMuted`,
`border`, `border2`, `accent`, `accentHover`, `accentGlow`, `active`, `danger`,
`success`, `warning`, and `info`.

Colors are strict `#RGB`, `#RGBA`, `#RRGGBB`, `#RRGGBBAA`, `rgb(...)`, or
`rgba(...)` values with bounded numeric channels. CSS functions such as `url`,
`var`, `calc`, and `color-mix` are forbidden.

Typography and shape fields:

- `font`: `system-sans`
- `fontMono`: `system-mono`
- `fontSize`: integer 11–18
- `fontSizeSm`: integer 9–16
- `fontSizeLg`: integer 12–24
- `radius`, `radiusSm`, `radiusLg`: integer 0–32
- `radiusPill`: integer 32–999

The Host maps font ids to trusted local stacks. Package strings never become
unparsed CSS.

### 5.2 `theme/shell.json`

```json
{
  "schema_version": 1,
  "dark_mode": true,
  "atmosphere": "glass-neon",
  "fx": { "glow": "soft", "motion": "reduced" },
  "background": {
    "kind": "asset",
    "opacity": 0.55,
    "overlay": 0.3,
    "focal_x": 0.5,
    "focal_y": 0.5
  },
  "icon": { "kind": "asset" }
}
```

- `atmosphere`: `none`, `soft-glow`, `gradient-line`, `aurora`, `sakura-mist`,
  `glass-neon`, or `paper-soft`.
- `fx.glow`: `off`, `soft`, or `vivid`.
- `fx.motion`: `full`, `reduced`, or `off`.
- `background.kind`: `none` or `asset`.
- `icon.kind`: `host_default` or `asset`.
- Opacity, overlay, and focal values are finite numbers from 0 through 1.

### 5.3 `theme/effects.css`

Optional visual-effects CSS is limited to 64 KiB and rendered in a dedicated
iframe with no scripts, same-origin privilege, network, images, fonts, media,
objects, forms, or child frames. The iframe is pointer-transparent and remains
inside the Host background layer.

The stylesheet may use ordinary CSS rules, gradients, masks, filters,
transforms, transitions, `@keyframes`, `@media`, and `@supports`. Its document
contains one `.theme-effects` element. Validated theme tokens are available as
CSS variables on the sandbox document root.

The validator rejects markup delimiters, CSS escapes, `@import`, `url(...)`,
`javascript:`, `expression`, `behavior`, and `-moz-binding`. These restrictions
are defense in depth in addition to the iframe and CSP boundary.

## 6. Animation IR v1

`animation/program.json` and `animation/fallback.json` use:

```json
{
  "schema_version": 1,
  "fps": 60,
  "max_particles": 240,
  "layers": [
    {
      "id": "bloom",
      "blend": "lighter",
      "opacity": 0.6,
      "nodes": [
        {
          "id": "petals",
          "type": "particle_emitter",
          "params": {
            "count": 180,
            "shape": "petal",
            "color": "#39C5BB",
            "size_min": 2,
            "size_max": 8,
            "speed_min": 4,
            "speed_max": 30,
            "lifetime_min": 2,
            "lifetime_max": 12,
            "direction": 1.57,
            "spread": 0.8
          }
        }
      ]
    }
  ]
}
```

Layers are evaluated in array order. Nodes are declarative operations, not a
general expression language. Allowed node types are:

- `gradient`
- `particle_emitter`
- `sprite_emitter`
- `starfield`
- `scan_lines`
- `path`
- `trail`
- `glow`
- `transform`
- `blend`
- `pointer_field`
- `pointer_swirl`
- `pulse_rings`
- `magic_mist`
- `rune_orbit`
- `light_ribbon`
- `local_time_curve`

Each node has exactly `id`, `type`, and `params`. Parameters are
type-specific; unknown parameters are rejected. Node ids are unique within the
program. There is no recursion, dynamic lookup, executable string, network,
storage, DOM, or Host object access.

`fallback.json` is required and must not contain `pointer_field`, `pointer_swirl`,
`sprite_emitter`, or more than 120 particles. The Host uses it for reduced
motion or when the primary program is disabled.

The package capabilities must agree with the IR:

- `pointer` is true iff the primary program contains `pointer_field` or `pointer_swirl`.
- `local_time` is true iff it contains `local_time_curve`.
- `reduced_motion` is true because a valid fallback is mandatory in v1.

## 7. Integrity and signature

`integrity.json`:

```json
{
  "schema_version": 1,
  "algorithm": "sha256",
  "files": {
    "manifest.json": {
      "sha256": "64-lowercase-hex-characters",
      "size": 1024
    }
  }
}
```

Every regular file except `integrity.json` and `signature.ed25519` is listed
exactly once. No listed path may be absent. Hashes are over exact entry bytes.

Market packages contain a raw 64-byte Ed25519 signature over the SHA-256 digest
of canonical `integrity.json`. Public-key selection and rotation are Host
configuration, not package input. Unsigned local packages may be installed only
with an `unverified-local` label after the same full validation.

## 8. ZIP validation

Before parsing package content, reject:

- absolute paths, drive-prefixed paths, `..`, `.`, empty segments, backslashes,
  NUL, control characters, or non-NFC names;
- duplicate raw or normalized paths, case-fold collisions, and file/directory
  collisions;
- encrypted entries, data descriptors that defeat configured limits, symlinks,
  devices, sockets, and other non-regular entries;
- nested archive extensions and forbidden executable/media extensions;
- limit violations from §3.

Raster dimensions and decoded-pixel totals are checked after safe image decode
and before re-encoding. `ThemePackageSpec` validates archive structure, JSON,
integrity, and encoded-byte limits; the Host image decoder remains responsible
for decoded-pixel checks.

## 9. Staged binary transfer

The Host owns browser upload quarantine:

1. Browser streams a file to a Host upload endpoint.
2. Host validates authentication, MIME, extension, compressed-byte limit, and
   stores it outside the static web root.
3. Host returns an opaque, short-lived, single-use `upload_ref` bound to user,
   session, operation, expected size, and hash.
4. The widget sends only `upload_ref` to `theme_package_import`.
5. Theme Builder redeems it through an authenticated Host channel.

Compiled downloads use the inverse `artifact_ref` contract. An `artifact_ref`
is provider-bound, user-bound, operation-bound, expiring, and includes expected
SHA-256 and size metadata. Arbitrary caller-provided URLs are forbidden.

## 10. Compliance

Protocol and Host implementations use:

```java
ThemePackageSpec spec = new ThemePackageSpec();
spec.assertValidManifest(manifest);
spec.assertValidTokens(tokens);
spec.assertValidShell(shell, manifest);
spec.assertValidAnimation(program, false);
spec.assertValidAnimation(fallback, true);
spec.assertValidPackage(zipInputStream);
```

A valid deterministic fixture and malicious archive fixtures are mandatory.
At minimum test traversal, duplicate normalized names, forbidden files, an
expanded-size bomb, invalid integrity, unknown JSON fields, invalid colors,
animation node/particle limits, and capability mismatches.
