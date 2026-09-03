# shared/css

**Host chrome CSS moved to world-one.**

Edit and package Host styles in:

`ones/world-one/src/main/resources/static/css/`

This directory still holds **theme generator outputs** used by Once sync:

- `themes/` — palette overlays (`bundle.css`, per-theme trees)
- `*-presets.json` — catalog metadata

Regenerate / sync:

```bash
node shared/theme/generate-aipp-css.mjs
# copies Host CSS from world-one + themes/presets from here → once / ones-shell
```

Do not add widget or Host shell CSS here. Widgets never ship `.css`; the Host page serves chrome.
