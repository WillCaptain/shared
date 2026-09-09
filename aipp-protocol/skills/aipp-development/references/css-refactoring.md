# AIPP CSS refactoring guide for coding assistants

Use this guide when auditing or refactoring an AIPP frontend. The normative
contract is [`../../../spec/widgets.md`](../../../spec/widgets.md) §4; this file
is the execution checklist.

## Target architecture

```text
active theme id
  -> shared --aipp-* token values
  -> shared .aipp-* primitives
  -> Host CSS and every AIPP stylesheet
```

Shared CSS owns reusable visual vocabulary. An AIPP stylesheet owns only the
small domain-specific layer that shared primitives cannot express.

## Required order of work

1. Inventory every stylesheet, `render.styles` declaration, embedded style
   string, `<style>` injection, and `element.style` assignment.
2. Move AIPP-specific selectors out of Host/shared CSS and into its AIPP.
3. Make widget markup compose shared primitives before retaining local rules.
4. Replace literal colors, private palettes, fonts, radii, and shadows with
   shared `var(--aipp-*)` tokens.
5. Move static JavaScript styling into classes. Do not inject `<style>` or
   assign `cssText` for component chrome.
6. Keep runtime values inline only for data-driven geometry. Expose them as
   narrowly named custom properties and let the stylesheet consume them.
7. If multiple AIPPs need the same missing component, add a provider-neutral
   primitive to `shared/css/aipp-primitives.css`; do not copy the rule.
8. Delete redundant local declarations after markup uses the shared primitive.
9. Run frontend guards, ESM parsing, package tests, and color/theme-id scans.

## Shared-first mapping

| Local concept | Shared composition |
|---|---|
| Root or ordinary surface | `aipp-root`, `aipp-panel` |
| Popup/dialog shell | `aipp-popup-shell` plus width modifier |
| Button | `aipp-btn` plus a semantic or icon modifier |
| Text input/select | `aipp-input` |
| Textarea | `aipp-textarea` |
| Tabs | `aipp-tabs`, `aipp-tab`, `aipp-tab--active` |
| List/card row | `aipp-list`, `aipp-list-item`, clickable/active modifiers |
| Badge/chip | `aipp-badge` semantic modifier or `aipp-chip` |
| Empty/error/message | `aipp-empty`, `aipp-error`, `aipp-message`, `aipp-muted` |
| Code | `aipp-code` |
| Actions layout | `aipp-btn-row`, `aipp-btn-row--end` |

Local classes may accompany these classes for domain layout, but must not
reimplement the primitive's ordinary visual chrome.

## Runtime geometry exception

Dynamic graph coordinates, measured popup coordinates, progress, zoom, and
canvas dimensions may vary at runtime. Store only the value inline:

```js
node.style.setProperty('--entity-x', `${x}px`);
node.style.setProperty('--entity-y', `${y}px`);
```

```css
.entity-node {
  left: var(--entity-x);
  top: var(--entity-y);
}
```

Do not use this exception for colors, borders, typography, padding, component
sizes, or other static chrome. Show/hide state should normally use `hidden` or
a state class; `style.display` remains allowed during legacy migration.

## Completion gate

- No AIPP identity appears in `shared/css`.
- Every app stylesheet is served by its AIPP and declared through
  `render.styles` or its protocol-defined Host-extension resource.
- No literal palette or theme-id selector exists in AIPP CSS.
- No injected stylesheet or embedded component CSS exists in widget JS.
- No static color/layout chrome is assigned through `element.style`.
- Shared classes are used wherever a matching primitive exists.
- Remaining local rules are domain-specific layout or behavior.
- Tests pass without CSS-guard exclusions.

## Audit report format

For each AIPP report `pass`, `deferred`, or `fail`, with evidence grouped by
ownership, primitive reuse, tokens, JavaScript styling, permitted geometry,
verification, and remaining exceptions.
