# Self-contained Ones themes

Each built-in theme is one directory under `themes/<theme-id>/`:

```text
theme.json
theme.css
resources/background.png   # optional
resources/icon.png         # optional
animation/program.json
animation/fallback.json
effects.css                 # optional sandbox-only decoration
```

`theme.json` owns metadata, palette tokens, resource declarations, default
background/animation behavior, and package identity. `theme.css` is loaded after
the stable shared CSS and must scope every Host rule to its own palette or
background attribute. Relative CSS URLs may only address `./resources/`.

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
