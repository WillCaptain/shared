# outline-editor

Shared **editor wire** for Outline Monaco hosts. Depends on the **outline** language module and **gcp**; hosts depend on this artifact, not on duplicating parse/infer/hover logic.

Full contract: [`../EDITOR_CONTRACT.md`](../EDITOR_CONTRACT.md).

## Layering

```text
outline-lang.js  →  HTTP  →  StatelessOutlineEditor  →  MetaExtractor  →  gcp
     (UI)              (this module)                  (outline)
```

| Module | Role |
|--------|------|
| **gcp** | Inference engine |
| **outline** | `MetaExtractor`: completions, hover resolution, `formatType`, `formatSignatureType` |
| **outline-editor** (here) | Prelude split, preamble cache, infer fork, markers, REST-shaped JSON |
| **outline-lang.js** | Monaco providers; renders `{ symbol }` via `renderSymbolMd` |

## Main API

`org.twelve.shared.outline.editor.StatelessOutlineEditor`

- `splitPreludeBody`, `inferWithPrelude`
- `validateMarkers`, `completionsWire`
- `hoverSymbol` — raw `{ name, kind, type }`
- `hoverSymbolResponse` — `{ symbol: … }` for browsers
- `inferReturnType`

`org.twelve.shared.outline.diagnostic.OutlineSyntaxDiagnostics` — lexer/parser markers.

## CI

```bash
./scripts/check-editor-contract.sh
```

## Tests

```bash
mvn -q test
```
