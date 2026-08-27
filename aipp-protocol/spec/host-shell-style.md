# Host shell style — theme, background, animation

> **Audience:** Host implementers (world-one), shared CSS authors, and Theme Builder implementers.
> **Scope:** Host shell only — **not** widget-local CSS. Widget rules remain in [`widgets.md`](widgets.md) §4.

---

## 1. Three background layers

The Host shell renders three independent layers behind the workspace UI. Layers 2 and 3 may be partially transparent so lower layers show through.

| Z-order | Layer | User control | DOM / CSS |
|---------|-------|--------------|-----------|
| 1 (bottom) | **Theme** | Basic tab: palette + atmosphere + token overrides | `:root` `--aipp-*` tokens; `data-aipp-palette`, `data-aipp-atmosphere`, `data-aipp-fx-*` |
| 2 (middle) | **Background image** | Advanced tab: preset wallpaper or uploaded image | `.aipp-shell-bg-wall`; `data-aipp-background`; `--aipp-shell-wall-image` |
| 3 (top) | **Background animation** | Advanced tab: animation preset or `none` | `#aipp-shell-bg-anim` sandbox iframe; canvas draw only |

**Hard rules**

1. Layers decorate the shell only — chat bubbles, canvas, and widgets must stay fully readable.
2. Layer 2 and 3 must use `pointer-events: none`.
3. Layer 3 must never read Host DOM, storage, cookies, or network.
4. Widgets must not depend on shell atmosphere/background attributes — only `--aipp-*` tokens.

---

## 2. Theme model (layer 1)

### 2.1 Source of truth

```
shared/theme/aipp-themes.json
        │
        ▼
shared/theme/generate-aipp-css.mjs
        │
        ├── shared/css/aipp-tokens.css
        ├── shared/css/themes/*.css
        └── shared/css/theme-presets.json   (catalog for UI)
```

Java: `AippThemes` reads classpath `/aipp-themes.json` (synced from shared JSON).

### 2.2 Complete theme package

Every built-in standard theme is a `.ones-theme` package generated from the same
source pipeline (`shared/theme/generate-standard-theme-packages.mjs`). The
catalog lives in `shared/theme/standard-theme-packages.json`; palette metadata
still originates from `aipp-themes.json` presets marked `standard: true`.

Richer standard themes (for example `ones.standard.hatsune-miku`) only add
optional assets — background, icon, animation IR — on top of the same manifest,
token, and shell contract used by palette-only packages such as
`ones.standard.dark` and `ones.standard.light`.

Legacy palette-only presets without `standard: true` remain CSS/catalog entries
until migrated; they are not special-cased relative to standard packages.

Each standard package entry declares:

- style tokens plus optional `atmosphere` and `fx`;
- a default `background`;
- a default `bgAnimation`;
- a trusted local `icon` (`id` and static `img/...` path);
- `standard: true` when it belongs in the built-in standard theme set.

The Host uses the icon for its function-bar Once mark and assistant avatar. Once
desktop receives the same icon id in the style bridge and may apply it to Oncer.
Theme assets must be shipped with the Host/Once package; remote icon URLs are not
accepted.

### 2.3 Style document (persisted)

```json
{
  "version": 2,
  "palette": "sakura-pop",
  "atmosphere": "sakura-mist",
  "fx": { "glow": "soft", "motion": "full" },
  "background": { "kind": "preset", "id": "deep-space" },
  "backgroundOverridden": true,
  "bgAnimation": "whole-day-sky",
  "bgAnimationOverridden": true,
  "overrides": {
    "accent": "#e26f9e"
  }
}
```

| Field | Type | Notes |
|-------|------|-------|
| `palette` | string | Palette id from `theme-presets.json` |
| `atmosphere` | string | `none` \| `soft-glow` \| `aurora` \| … |
| `fx.glow` | string | `off` \| `soft` \| `vivid` |
| `fx.motion` | string | `full` \| `reduced` \| `off` |
| `background` | object | `{ kind: "none" }` \| `{ kind: "preset", id }` \| `{ kind: "custom" }` |
| `backgroundOverridden` | boolean | `false` means use the selected theme package's default background |
| `bgAnimation` | string | Animation id from `bg-animation-presets.json`, or `none` |
| `bgAnimationOverridden` | boolean | `false` means use the selected theme package's default animation |
| `overrides` | object | Optional `--aipp-*` token overrides (advanced) |

Advanced background and animation cards set the corresponding override flag.
Selecting the “Theme default”/empty card clears that flag; it does not force an
effective `none`. Effective values are always resolved as
`advanced override ?? selected theme default`.

Host applies attributes on `document.documentElement` and mirrors palette tokens to `[data-aipp-widget-mounted]` roots.

### 2.4 Catalog labels

All preset labels in JSON catalogs use **LocalizedString**:

```json
"label": { "en": "Sakura Pop", "zh": "樱花少女" }
```

Host UI must resolve labels with session `language` (see [`localization.md`](localization.md)).

---

## 3. Background image (layer 2)

### 3.1 Presets

Catalog: `shared/css/background-presets.json` → copied to Host `css/background-presets.json`.

CSS: `shared/css/aipp-backgrounds.css` defines `html[data-aipp-background="<id>"]` rules.

### 3.2 Custom upload

| Surface | Storage | Notes |
|---------|---------|-------|
| Browser | `localStorage` data URL | Ephemeral; not synced to server |
| Once desktop | Local file path + `file://` or app bridge URL | `pickBackgroundImage` bridge |

Server `/api/settings` stores preset background only (`kind: preset` \| `none`). Custom image bytes never leave the client unless a future user-library feature explicitly uploads them.

### 3.3 Opacity

Wall layer uses `--aipp-shell-wall-opacity` (preset) or `--aipp-shell-wall-custom-opacity` (custom). Overlay gradient `--aipp-shell-wall-overlay` keeps workspace text readable.

---

## 4. Background animation (layer 3)

### 4.1 Catalog

`css/bg-animation-presets.json`:

```json
{
  "version": 1,
  "animations": [
    {
      "id": "whole-day-sky",
      "label": { "en": "A Whole Day", "zh": "一日天空" },
      "description": { "en": "…", "zh": "…" }
    }
  ]
}
```

Metadata only — **no executable code** in JSON catalogs.

### 4.2 Sandbox runner (required)

Implementation reference: `world-one/src/main/resources/static/shell/ones-bg-animation.js`.

| Requirement | Detail |
|-------------|--------|
| Isolation | `<iframe sandbox="allow-scripts">` — **no** `allow-same-origin`, `allow-popups`, `allow-forms`, `allow-modals` |
| CSP (iframe) | `default-src 'none'`; `connect-src 'none'`; `img-src 'none'`; no `unsafe-eval` |
| IPC | Parent sends allow-listed control messages only: `{ type, id, w, h }` and optional `{ type:"pointer", x, y, active }`; `x/y` are clamped normalized coordinates — **never** DOM data or user-supplied code strings |
| Registry | Draw functions compiled into iframe `srcdoc` at Host build time; frozen `REGISTRY` keyed by id |
| Message auth | Iframe accepts `postMessage` only when `e.source === parent` |
| Canvas API | Draw functions receive `(ctx, w, h, t, state)` only |
| Deny | `fetch`, `XMLHttpRequest`, `importScripts`, DOM access to parent, `localStorage`, `indexedDB`, workers |

Interactive presets may react to pointer movement. The Host performs coordinate normalization
and animation-frame throttling, then sends only `x`, `y`, and `active`. The sandbox must clamp
all values again and must not receive element identity, target text, button state, or raw events.

### 4.3 User-authored animations (future)

User-submitted animation source is **not** executed via `eval`, `new Function`, or dynamic `postMessage` code injection.

Approved path:

1. User drafts an animation in the independent Theme Builder AIPP.
2. Theme Builder compiles authoring source to a bounded declarative animation IR.
3. Host validates package integrity, IR structure/cost, assets, and sandbox smoke tests.
4. Host runs only validated IR through a trusted built-in interpreter. Package
   source files are retained for editing only and never receive runtime authority.

---

## 5. Theme Builder and user style library (future)

Theme authoring is owned by the independent `ones-theme-builder` AIPP. Host runtime
authority remains in world-one.

| Concern | Owner |
|---------|-------|
| Mutable projects, immutable versions, package compile/import/export | Theme Builder AIPP |
| Private user library metadata and market listings | Theme Builder AIPP |
| Package validation, install, preview, apply, uninstall | world-one Host |
| Token-to-CSS projection and animation IR interpreter | world-one Host |
| Standard builtin themes | shared catalogs + world-one Host |

Theme Builder must not inject Host CSS or animation code directly. It exchanges
opaque artifact references with a Host-owned Theme Runtime Broker; the Host
fetches only from the registered provider, validates the package, and applies
safe typed values and declarative animation IR.

Binary theme ZIPs must not travel through the current text-oriented widget
upload/tool-argument path. A future package spec must define Host-quarantined,
per-user staged uploads represented by opaque, short-lived `upload_ref` values.

The Theme Builder serves a router-promoted `ones_style` skill. Small
conversational changes may update the current user's style without opening the
canvas, but the resulting complete style is captured as a private Builder
project/revision.

Package validation follows [`theme-packages.md`](theme-packages.md). See the implementation design:
`world-one/docs/ones-style-builder-design.md`.

---

## 6. Host CSS file map

| File | Layer |
|------|-------|
| `aipp-tokens.css` | Theme tokens |
| `css/themes/bundle.css` | Palette overlays |
| `aipp-atmosphere.css` | Atmosphere (shell edge effects) |
| `aipp-backgrounds.css` | Background wall + animation mount |
| `aipp-shell.css` | Style panel UI + shell layering |
| `aipp-primitives.css` | Widget components (not shell decoration) |

Regenerate theme CSS after editing `aipp-themes.json`:

```bash
node shared/theme/generate-aipp-css.mjs
```

---

## 7. Compliance checklist

Host implementers:

- [ ] Three layers mounted in order: theme → wall → animation
- [ ] Animation iframe uses sandbox + CSP from §4.2
- [ ] No runtime code injection into animation iframe
- [ ] Catalog JSON contains labels in `en` + `zh` (minimum)
- [ ] Widget iframes receive `postMessage({ type:'aippTheme', … })` on theme change
- [ ] Custom background bytes not stored in server settings JSON by default

Future Theme Builder AIPP:

- [ ] Per-user library keyed by authenticated user id
- [ ] Deterministic `.ones-theme` package compile/import/export
- [ ] No customer executable code in installed packages
- [ ] Validation gate before any animation IR enters the interpreter
- [ ] Share/publish is explicit opt-in
