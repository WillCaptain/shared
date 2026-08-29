# Self-contained Ones themes

Each built-in theme is one directory under `themes/<theme-id>/`:

```text
theme.json
theme.css
resources/background-original.png # optional lossless editing/update source; never packaged
resources/background.jpg          # optional optimized runtime asset, maximum 500 KiB
resources/icon.png         # optional
resources/preview.png      # lightweight theme/background card
animation/program.json
animation/fallback.json
animation/preview.png      # lightweight animation poster (`animation.preview_asset`)
effects.css                 # optional sandbox-only decoration
```

`theme.json` owns metadata, palette tokens, resource declarations, default
background/animation behavior, and package identity. `theme.css` is loaded after
the stable shared CSS and must scope every Host rule to its own palette or
background attribute. Relative CSS URLs may only address `./resources/`.

When a theme has a background, both preview PNGs are required. They are
presentation assets, not runtime substitutes: the Host uses them for fast
initial cards, then runs `animation/program.json` in the existing sandbox only
while an animation card is hovered or focused. Keep previews small (currently
320 px on the longest edge), so opening Advanced never downloads the complete
full-resolution wallpaper set.

Runtime backgrounds must be no larger than 500 KiB and no larger than
1920x1200. Keep the lossless source as `resources/background-original.png` for
future theme updates, but reference only the optimized JPEG from `theme.json`.
Original backgrounds are source artifacts and must not be included in
`.ones-theme` packages. Apply the same convention to unusually large icons or
animation sprites: retain `*-original.png` beside the theme source and reference
only the optimized runtime asset. Runtime icons should be 128x128 and at most
100 KiB; individual animation sprites should be at most 500 KiB.

`theme-base.json`, `background-base.json`, and `bg-animation-base.json` contain
only stable shared defaults. The build scans theme directories; there is no
hand-maintained theme-id allowlist.
It generates compatibility JSON, `shared/css/themes/<id>/`, Once copies, and
standard `.ones-theme` packages. Adding a built-in theme therefore adds one
directory and runs:

```bash
node shared/theme/generate-aipp-css.mjs
node shared/theme/generate-standard-theme-packages.mjs --compile
```

Built-in Host CSS is trusted repository code. User package effect CSS remains
inside the scriptless, network-denied effects iframe. Host chrome extensions use
strictly validated declarative shell capabilities; uploaded CSS never gains
unrestricted access to Host controls.
