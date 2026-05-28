# outline-editor

Java backend for the **stateless Monaco editor** wire contract used by
[`../js/outline-lang.js`](../js/outline-lang.js).

## Scope

| Module | Responsibility |
|--------|----------------|
| **outline** | Language: parser, AST converter, interpreter hooks |
| **gcp** | Type inference, `MetaExtractor`, symbol environments |
| **outline-editor** (this) | Host orchestration: `prelude_length` split, preamble cache, validate/infer/hover/completions, Monaco marker shaping |

Hosts (world-entitir, 12th playground, decision-exec-aipp) depend on **outline-editor**, not on editor classes inside **outline**.

## Main types

- `org.twelve.shared.outline.editor.StatelessOutlineEditor`
- `org.twelve.shared.outline.diagnostic.OutlineSyntaxDiagnostics`
