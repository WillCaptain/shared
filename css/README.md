# shared/css

This directory owns common Host/AIPP CSS and theme generator outputs:

- `aipp-tokens.css`, `aipp-primitives.css` — shared tokens and reusable primitives
- `aipp-sys-widgets.css`, `aipp-shell.css`, `aipp-atmosphere.css`, `aipp-backgrounds.css` — Host/system chrome

- `themes/` — palette overlays (`bundle.css`, per-theme trees)
- `*-presets.json` — catalog metadata

Regenerate / sync:

```bash
node shared/theme/generate-aipp-css.mjs
# copies Host CSS from world-one + themes/presets from here → once / ones-shell
```

Do not add an AIPP's particular selectors here. Put those beside the implementing widget and
declare them through the widget manifest's `render.styles` array.
