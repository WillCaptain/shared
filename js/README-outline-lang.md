# outline-lang.js — Drop-in Outline language support for Monaco

`outline-lang.js` is a self-contained browser SDK that turns any [Monaco
Editor](https://microsoft.github.io/monaco-editor/) instance into a full
Outline editor — syntax highlighting, completion, hover, and diagnostics —
without your page having to know anything about Monaco internals.

Everything lives in one file. Drop it on a page, point it at a Monaco CDN,
and give it four small backend endpoints; you get an IDE-class Outline
editor.

---

## 1. Quick start

```html
<!-- 1. A container -->
<div id="editor" style="height: 300px; border: 1px solid #333;"></div>

<!-- 2. Load the SDK (Monaco itself is lazy-loaded from a CDN) -->
<script src="/shared/js/outline-lang.js"></script>

<script>
  OutlineLang.createEditor(document.getElementById('editor'), {
    value: 'employees.filter(e -> e.status == EmployeeStatus.PENDING)',

    // ── Backend routing ──────────────────────────────────────────────────
    completions: {
      url: '/api/completions',
      getExtraBody: (code, offset) => ({
        session_id: 'world-eai-onboarding',
        entity_type: 'Employee',     // optional; only for trigger/action editors
      }),
    },
    hoverOptions: {
      hoverUrl: '/api/code-hover',
      getExtraBody: (code, offset) => ({
        session_id: 'world-eai-onboarding',
        entity_type: 'Employee',
      }),
    },
    diagnostics: {
      validateUrl: '/api/validate',
      getRequestBody: (code) => ({
        session_id: 'world-eai-onboarding',
        entity_type: 'Employee',
      }),
    },
    typeMapUrl: '/api/session-typemap?session_id=world-eai-onboarding',
  }).then(({ editor }) => {
    editor.onDidChangeModelContent(() => console.log(editor.getValue()));
  });
</script>
```

That's the whole integration. The SDK will:

- Lazy-load Monaco from a CDN if it isn't already on the page.
- Register the `outline` language (tokenizer, theme, brackets, indentation).
- Install **one** global completion / hover / diagnostics provider and wire
  **your editor's** endpoints through a per-editor WeakMap — so multiple
  editors on the same page can target different sessions safely.
- Apply `quickSuggestions` so typing the first character of an identifier
  (e.g. `e`) shows matching items (`employee`, `employees`, `Employee`,
  `EmployeeStatus`, …) — not only after a `.`.

---

## 2. Backend contract

`outline-lang.js` is language-server-agnostic. You only need to expose four
HTTP endpoints — typically backed by whatever type-checker / parser you
already ship. All bodies are `application/json`.

> Any field your backend does not need can be ignored; only the fields shown
> below are guaranteed to be sent.

### 2.1 `POST /api/completions`

Called when the user triggers completion (dot, space, or typing a letter).

**Request**

```json
{
  "code":       "employees.filter(e -> e.st",
  "offset":     27,
  "session_id": "world-eai-onboarding",
  "entity_type":"Employee"
}
```

- `code`   – full editor buffer.
- `offset` – zero-based cursor offset inside `code`.
- Any extra keys returned by your `completions.getExtraBody` are merged in.

**Response**

```json
{
  "items": [
    {
      "label":         "status",
      "detail":        "Status",
      "kind":          "property",
      "documentation": "Enum value · current HR status",
      "origin":        "own",
      "sortText":      "01_status"
    }
  ]
}
```

- `kind` is one of `"property" | "method" | "outline" | "keyword" | "builtin" | "variable"`
  (the last is the default).
- `label` is required. All other fields are optional.
- For **bare-identifier completion** (no trailing `.`), return in-scope
  variables, collections, enum/entity names and primitives; Monaco handles
  prefix filtering client-side.
- For **member completion** (trailing `.`), return only the receiver's
  members. For enum receivers, return the enum's options as `property`
  items.

### 2.2 `POST /api/validate`

Called on every keystroke (debounced by `diagnostics.debounceMs`, default
~250 ms).

**Request**

```json
{
  "code":            "...",
  "prelude_length":  4096,
  "session_id":      "world-eai-onboarding",
  "entity_type":     "Employee"
}
```

- `code` — full wire source: `prelude + wrap(userBody)` when using `inferenceFromContext`.
- `prelude_length` — character length of `prelude`; server parses only the suffix.

**Response**

```json
{
  "markers": [
    {
      "message":     "Unknown field `stat`",
      "severity":    8,
      "startLine":   2,
      "startColumn": 0,
      "endLine":     2,
      "endColumn":   10
    }
  ]
}
```

- `severity`: `8` = Error, `4` = Warning (Monaco `MarkerSeverity`).
- `startLine` / `endLine`: **1-based**, relative to the **post-prelude strip** the server parsed (wrap envelope when present). Do **not** shift markers for prelude on the server — `outline-lang.js` subtracts only `wrap.open` lines to map onto the user-visible editor buffer.

### 2.3 `POST /api/code-hover`

Called when the user hovers a symbol whose type isn't already in the
session type map.

**Request** — same shape as `/api/completions` (`code`, `offset`, plus
`session_id` / `entity_type` from `hoverOptions.getExtraBody`).

**Response**

```json
{ "markdown": "**employee** : *`Employee`*\n\nThe entity bound to this trigger body." }
```

If there's nothing to say, return `{ "markdown": null }` or `404`.

### 2.4 `GET /api/session-typemap?session_id=…`

Called **once** on editor init. Supplies the cheap/common type lookups so
every single-word hover doesn't hit the server.

**Response**

```json
{
  "Employee":       "**Employee** — Entity · 12 fields",
  "EmployeeStatus": "**EmployeeStatus** — Enum · 4 options",
  "employees":      "**employees** : *`VirtualSet<Employee>`*"
}
```

Map of identifier → Markdown hover content. The SDK merges this with its
built-in primitive types (`String`, `Int`, …).

### 2.5 `POST /api/infer`

Debounced return-type inference (action signature preview, decision executor
output type, trigger Bool check, reactor listener type, …).

**Request (stateless — action / trigger / reactor editors)**

Same wire shape as validate/completions/hover:

```json
{
  "code": "…full prelude + wrapped user body…",
  "offset": 4120,
  "prelude_length": 4096
}
```

**Request (decision executor — raw body, no prelude wire)**

```json
{
  "session_id": "canvas-session-uuid",
  "code": "decision { … }",
  "upstreams": [{ "name": "upstream", "type": "Employee" }],
  "parameters": []
}
```

**Response**

```json
{ "status": "ok", "returnType": "SalaryRecord" }
```

or `{ "status": "error", "error": "…", "returnType": "?" }`.

Legacy aliases `POST /api/infer-action-type` and
`POST /api/infer-decision-executor` remain; new hosts should use
`POST /api/infer` only.

---

## 3. Manifest-driven editors (recommended)

Hosts expose a **GET manifest** that declares static preamble, wrap envelope,
and endpoint URLs. Widgets fetch once, then pass dynamic wrap/callbacks via
`inferenceFromContext` — no hardcoded endpoints in widget code.

```js
const ctx = await OutlineLang.loadEditorContext('/api/outline-editor/action?session_id=…');
OutlineLang.createEditor(container, {
  inference: OutlineLang.inferenceFromContext(ctx, {
    wrap: (code, offset) => ({ code: open + code + close, offset: offset + open.length }),
    mapInferResponse: (data) => ({ kind: 'ok', text: data.returnType }),
    onInferStatus: (status, raw) => { /* optional: raw is the server JSON */ },
  }),
});
```

| Function | Purpose |
| -------- | ------- |
| `loadEditorContext(url)` | Fetch + normalize a host manifest |
| `normalizeEditorManifest(raw)` | Canonical shape (`wrap`, `signature`, `urls`) |
| `inferenceFromContext(ctx, options)` | Build `inference` config for `createEditor` |
| `envelopeWrap({ open, close })` | Static wrap helper from manifest `wrap` |

Manifest shape:

```json
{
  "preamble": "outline Employee = …\n",
  "wrap": { "open": "(listener: ReactorListener) -> {\n", "close": "\n}" },
  "signature": "(listener: ReactorListener) -> Unit",
  "urls": {
    "validate": "/api/validate",
    "infer": "/api/infer",
    "completions": "/api/completions",
    "hover": "/api/code-hover",
    "signature": "/api/signature-help",
    "typeMap": "/api/schema-types?session_id=…"
  }
}
```

The SDK expands `inference` into `completions`, `diagnostics`, `hoverOptions`,
`signatureHelp`, and `infer` — all sharing one `baseBody(code, offset)`:
`{ code: prelude + wrap(body), offset, prelude_length, …extras }`.

---

## 4. API surface

```js
OutlineLang.createEditor(container, options): Promise<{ editor, scheduleDiagnostics }>
```

The only function most integrations need. See `createEditor`'s JSDoc in
`outline-lang.js` for the full option list.

Lower-level building blocks, all exported on `window.OutlineLang`:

| Function                         | Purpose                                                               |
| -------------------------------- | --------------------------------------------------------------------- |
| `registerLanguage()`             | Install the `outline` tokenizer/theme/brackets. Idempotent.           |
| `registerCompletions(opts)`      | Install the shared completion provider. Called automatically.         |
| `registerHover(opts)`            | Install the shared hover provider.                                    |
| `createDiagnostics(opts)`        | Build a debounced `scheduleDiagnostics()` for a given editor.         |
| `attachModelOptions(model, opts)`| Attach per-editor routing (`completions`, `hoverOptions`) to a model. |
| `loadTypeMap(url, force?)`       | Fetch/refresh the session type map.                                   |
| `setTypeMap(obj)` / `lookupType(word)` | Programmatic control of the in-memory type map.                 |
| `setHoverFallback(fn)`           | Dynamic hover fallback: `(word, code, offset) => Promise<string>`.    |
| `createInfer(opts)`              | Debounced infer scheduler (returns fn with `.runNow()`).              |
| `envelopeWrap(spec)`             | Build wrap fn from manifest `{ open, close }`.                        |
| `loadEditorContext(url)`         | Fetch + normalize host manifest.                                      |
| `inferenceFromContext(ctx, opts)`| Build unified `inference` config.                                     |
| `normalizeEditorManifest(raw)`   | Normalize legacy manifest aliases.                                    |

---

## 5. Multi-editor pages

Each call to `createEditor` registers the shared providers once and then
binds **that editor's model** to **that editor's endpoints** via
`attachModelOptions`. You can therefore host N editors on one page, each
pointing at a different `session_id`, without any cross-talk.

If you create a Monaco editor yourself (without `createEditor`) and still
want Outline behavior, call:

```js
OutlineLang.registerLanguage();
OutlineLang.registerCompletions({});
OutlineLang.registerHover({});
OutlineLang.attachModelOptions(editor.getModel(), {
  completions:  { url: '/api/completions', getExtraBody: () => ({ session_id: '...' }) },
  hoverOptions: { hoverUrl: '/api/code-hover', getExtraBody: () => ({ session_id: '...' }) },
});
```

---

## 6. Minimum-viable backend

If you only want highlighting + diagnostics (no completion / hover), skip
the `completions`, `hoverOptions`, and `typeMapUrl` options and implement
only `POST /api/validate`. The editor still renders, parses, and shows
error squiggles.

Conversely, if your backend can't do diagnostics yet, omit the
`diagnostics` option; completion and hover still work.

---

## 7. Styling & theming

The built-in `outline-dark` theme is registered automatically. To use your
own theme, pass `editorOptions: { theme: 'my-theme' }` after registering it
with `monaco.editor.defineTheme()`.
