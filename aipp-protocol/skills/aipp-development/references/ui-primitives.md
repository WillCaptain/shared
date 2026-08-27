# AIPP UI primitives (index)

> Thin index. Normative: [`../../../spec/widgets.md`](../../../spec/widgets.md) §4.
> **Source of truth on disk:** `shared/theme/aipp-themes.json` → `shared/css/aipp-tokens.css`; hand-maintained `shared/css/aipp-primitives.css`.

## Rules

1. Host loads shared CSS before any widget mounts — widgets **must not** ship local CSS (`widgets/**/*.css`, injected `<style>`, hardcoded hex, `element.style` layout chrome).
2. Build markup with shared classes (`aipp-btn aipp-btn--primary`, `aipp-list-item`, …).
3. For custom layout only, use `var(--aipp-*)` tokens — never invent parallel colors. Sys widgets may import `AIPP_COLOR` / `iconColor()` from Host `sys-i18n.js` when applicable.

## CSS files (Host loads)

| File | Role |
|------|------|
| `shared/css/aipp-tokens.css` | All `--aipp-*` variables (+ host compat aliases) |
| `shared/css/aipp-primitives.css` | Shared `.aipp-*` component classes |
| `shared/css/aipp-sys-widgets.css` | System widget chrome |
| `shared/css/themes/light.css` | Light preset overlay (`[data-aipp-theme="light"]` compat) |
| `shared/css/themes/bundle.css` | All non-dark palette overlays (`[data-aipp-palette]`) |
| `shared/css/aipp-atmosphere.css` | Host shell atmosphere only — `data-aipp-atmosphere`, `data-aipp-fx-glow`, `data-aipp-fx-motion` (widgets must not depend on these) |
| `shared/css/aipp-backgrounds.css` | Host shell wallpaper presets — `data-aipp-background` (+ runtime `--aipp-shell-wall-image` for Once custom) |
| `shared/css/aipp-shell.css` | Ones Style panel + Host shell layering (`.aipp-host-shell`, `.aipp-shell-bg`) |
| `shared/css/bg-animation-presets.json` | Animation catalog metadata (labels only — no code) |

Normative shell style contract: [`../../../spec/host-shell-style.md`](../../../spec/host-shell-style.md).

## Required tokens

`--aipp-bg`, `--aipp-surface`, `--aipp-surface2`, `--aipp-surface3`,
`--aipp-text`, `--aipp-text-dim`, `--aipp-text-muted`,
`--aipp-border`, `--aipp-border2`,
`--aipp-accent`, `--aipp-accent-hover`, `--aipp-accent-glow`, `--aipp-active`,
`--aipp-danger`, `--aipp-success`, `--aipp-warning`, `--aipp-info`,
`--aipp-font`, `--aipp-font-mono`, `--aipp-font-size`, `--aipp-font-size-sm`, `--aipp-font-size-lg`,
`--aipp-radius`, `--aipp-radius-sm`, `--aipp-radius-lg`, `--aipp-radius-pill`

## Class cheatsheet (from `aipp-primitives.css`)

**Shell / layout:** `aipp-root`, `aipp-panel`, `aipp-panel--danger`, `aipp-popup-shell`, `aipp-popup-shell--wide`, `aipp-popup-title`, `aipp-popup-body`, `aipp-row`, `aipp-col`, `aipp-toolbar`, `aipp-scroll-panel`, `aipp-section--dashed`

**Typography / feedback:** `aipp-title`, `aipp-message`, `aipp-muted`, `aipp-error`, `aipp-warn`, `aipp-empty`, `aipp-callout`, `aipp-hint--warning`, `aipp-code`, `aipp-spinner`

**Actions:** `aipp-btn`, `aipp-btn--primary`, `aipp-btn--secondary`, `aipp-btn--danger`, `aipp-btn--ghost`, `aipp-btn--icon`, `aipp-btn--busy`, `aipp-btn-row`, `aipp-btn-row--end`

**Forms:** `aipp-field`, `aipp-label`, `aipp-input`, `aipp-textarea`, `aipp-input--error`, `aipp-password`, `aipp-password-toggle`

**Account / avatar:** `aipp-avatar`, `aipp-avatar--clickable`, `aipp-row--gap-sm`, `aipp-row--gap-md`, `aipp-col--gap-md`

**Errors:** `aipp-error`, `aipp-widget-load-error`

**Lists / chrome:** `aipp-list`, `aipp-list--boxed`, `aipp-list-item`, `aipp-list-item--clickable`, `aipp-list-item--active`, `aipp-tabs`, `aipp-tab`, `aipp-tab--active`, `aipp-chip`, `aipp-badge`, `aipp-badge--accent|success|warning|danger|info|muted`

Full class list: grep `^\.aipp-` in `shared/css/aipp-primitives.css`. Regenerate tokens via `shared/theme/generate-aipp-css.mjs` when theme JSON changes.
