# Outline editor contract

Every **active** Outline Monaco host (12th playground, world-entitir action/schema editors, decision-reactor) shares one stack. Deprecated hosts (e.g. entitir query editor) are out of scope.

## Module boundaries

| Layer | Maven artifact | Package / path | Responsibility |
|-------|----------------|----------------|----------------|
| **Language** | `outline` | `org.twelve.outline.meta.MetaExtractor` | Parser-facing IDE semantics: completions, hover **resolution**, `formatType`, `formatSignatureType`, meta. **No** prelude wire, HTTP, Monaco. |
| **Editor wire** | `outline-editor` | `org.twelve.shared.outline.editor` | Shared backend: `prelude_length`, preamble cache, parse+infer fork, markers, REST helpers. Calls **into** `outline` only. |
| **Editor UI** | (static) | `shared/js/outline-lang.js` | Monaco providers, `createEditor`, `renderSymbolMd`. **No** duplicate GCP type rules. |

```text
Host page
  → OutlineLang.createEditor / inferenceFromContext
  → HTTP (host controller)
  → StatelessOutlineEditor          (outline-editor)
  → MetaExtractor                   (outline)
  → gcp inference
```

**Dependency rule:** `outline-editor` → `outline` → `gcp`. Never the reverse.

## Public APIs (use only these)

### Language (`outline` / `MetaExtractor`)

| API | Use for |
|-----|---------|
| `formatType(String)` | All user-visible type labels (hover, symbols, infer status) |
| `formatSignatureType(String)` | Signature-help cards (may abbreviate large entity types) |
| `completionsAt(...)` | Dot / identifier completion |
| `parseCodeForCompletion(code, offset)` | Dot-trigger parse repair (`.completion` probe) |
| `inferSourceForDotCompletion(code, offset)` | Scoped infer slice for dot completion (outlines + enclosing `let`/`->` block) |
| `resolveHoverSymbol(ast, word, start)` | Hover on an **already inferred** AST |
| `symbolResponse(...)` | JSON `{ symbol: { name, kind, type } }` for the browser |
| `identifierSpanAt(code, offset)` | Word span at cursor |
| `splitPreludeWire`, `seedUserAstFromPreamble`, `outerScopeFromPreamble` | Prelude protocol helpers |

### Wire (`outline-editor` / `StatelessOutlineEditor`)

| API | Use for |
|-----|---------|
| `splitPreludeBody(body, code)` | `prelude_length` split |
| `inferWithPrelude(prelude, userCode)` | Parse + infer user slice (cached; same as `inferWithPreludeCached`) |
| `inferWithPreludeCached(prelude, userCode)` | Parse + infer with LRU cache by prelude + source hash |
| `validateMarkers` / `validateCombinedWire` | Diagnostics markers |
| `completions` / `completionsWire` | Completion JSON (uses `inferSourceForDotCompletion` + cache) |
| `hoverSymbol` / `hoverSymbolResponse` | Hover (reuses cached infer when possible, then `MetaExtractor`) |
| `inferReturnType` | Status bar / infer endpoint |

### UI (`outline-lang.js`)

| API | Use for |
|-----|---------|
| `createEditor(container, opts)` | Full editor setup |
| `renderSymbolMd(sym)` | Hover + static type map markdown |
| `formatTypeLabel(t)` | Lazy-artifact cleanup only (types are pre-formatted on the wire) |
| `setSymbols` / `setTypeMap` | Static hover fallback |

## Wire shapes

### Hover (`POST …/code-hover`)

```json
{
  "symbol": {
    "name": "e",
    "kind": "variable",
    "type": "Int"
  }
}
```

- `type` must already be `MetaExtractor.formatType` output.
- Client: `OutlineLang.renderSymbolMd(data.symbol)`.
- Do **not** build markdown in Java hosts.

### Completions

`{ "items": [ { "label", "detail", "kind", ... } ] }` from `StatelessOutlineEditor.completionsWire`.

## Nullable / hover semantics (language, not hosts)

- `let` + `T?` or `??` + definite initializer → narrows to `T` on the symbol.
- `var` + same → stays `T?`.
- Implemented in **gcp** assignment + `MetaExtractor.resolveHoverSymbol`; hosts must not special-case.

## Forbidden in host apps

- New `private String formatType` with `replace("INTEGER", …)` (delegate to `MetaExtractor.formatType`).
- New `simplifyType` / hover markdown builders for **Outline code** (use `resolveHoverSymbol` + `symbolResponse`).
- Hand-built hover markdown for inferred symbols (schema-hover ontology docs are OK as a separate endpoint).

## CI

From repo root:

```bash
shared/outline-editor/scripts/check-editor-contract.sh
```

Fails if duplicate type-formatting rules appear outside `MetaExtractor.formatType`.

## PR checklist

- [ ] No new editor logic in `app.js` except UI chrome (`formatTypeHtml` colors OK)
- [ ] Hover uses `symbol` + `renderSymbolMd`
- [ ] Types formatted only via `MetaExtractor.formatType` / `formatSignatureType`
- [ ] `outline-editor` tests green
- [ ] `outline` + `gcp` tests green
