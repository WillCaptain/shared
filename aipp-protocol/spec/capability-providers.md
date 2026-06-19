# Capability Providers (shared cross-cutting capabilities)

> A **capability provider** is an ordinary AIPP whose tools are meant to be used
> by *other* apps (and by the Host itself), not just inside its own sessions.
> Examples: **memory-one** (`memory_*`), **outline-one** (`outline_*`).
>
> There is **no special provider framework**. A provider is a normal registered
> AIPP. "Provider" is a usage pattern built from three things you already have:
> registration, stable global tool names, and an ambient prompt contribution.

## 1. What makes an app a provider

| Ingredient | Mechanism | Effect |
|------------|-----------|--------|
| **Registration** | `POST /api/registry/install {app_id, base_url}` (`host-registration.md`) | Host indexes every tool name → this app's `base_url` in its global `toolIndex` |
| **Stable global tool names** | `outline_parse`, `memory_query`, … in `/api/tools` | Any session routes the tool **by name** to the owning app — `registry.findAppForTool(name)` |
| **Ambient policy** | `prompt_contributions[layer=ambient_prompt]` (`host-decoupling.md` §6) | Every session learns *when* to reach for the capability, even from other apps' sessions |

That is the whole contract. memory-one and outline-one differ only in their tool
set and (for memory) `lifecycle: pre_turn`; outline-one is purely on-demand.

> Tool names are global. A provider must namespace its tools (`outline_*`,
> `memory_*`) so they do not collide with another app's tools in `toolIndex`.

## 2. How consumers depend on a capability

Consumers depend on the **capability (tool names)**, never on a specific provider
`app_id`. The Host routes by name, so the provider can be renamed or replaced
without touching the consumer.

- In a **skill**, list the provider's tools in `allowed-tools` and state in the
  `description` that the dependency is on the capability "from any installed
  AIPP". (See world-entitir's `decision_trigger_code` skill.)
- The skill's procedure should degrade gracefully: if a required tool cannot be
  called, tell the user the capability is not installed instead of failing
  silently.

Do **not** hardcode a provider's `base_url` in another app. Cross-AIPP calls go
through the Host (tool routing / proxy), exactly like any other tool call.

## 3. Declaring a dependency: `requires`

An app MAY declare, at the **`/api/tools` root**, the tool names it depends on
from a provider:

```json
{
  "app": "world",
  "version": "1.0",
  "requires": ["outline_parse", "outline_infer", "outline_completion", "outline_grammar"],
  "tools": [ ... ]
}
```

Semantics:

- `requires` is an array of **tool names** (capability), not app ids.
- It is **advisory**: the Host records it and, at registration, logs a warning
  for any name not currently resolvable in its `toolIndex`.
- It is **ordering-tolerant**: a missing dependency may simply mean the provider
  registers later, so an unmet `requires` is never a load failure.
- The Host exposes the current unmet set for diagnostics
  (`AppRegistry.unmetRequirements()` in world-one).

`requires` changes nothing about routing — it only turns a silent "tool not
found" at call time into an explicit warning at install time.

## 3a. Ambient prompt convention (token budget)

Every provider's `ambient_prompt` is charged to **every session's** system prompt,
and this cost grows with each installed provider. Keep it disciplined:

- **One** `ambient_prompt` contribution per provider, **≤ 2 sentences**: say what
  the capability is and *when* to reach for it, then point at the skill/tools.
- **Detail lives in the skill** (progressive disclosure via `SKILL.md`), never in
  the ambient prompt. The prompt is a pointer, not a manual.
- Use `lifecycle: pre_turn` **only for stateful capabilities** that must be
  refreshed every turn (e.g. memory). Stateless, on-demand capabilities (e.g.
  Outline) stay prompt-only + skill-router activated — no `pre_turn`.

Example (outline-one): *"Outline is this ecosystem's standard script language.
For any Outline task (write / type-check / run a snippet), use the `outline_code`
skill or the `outline_*` tools instead of hand-writing it — the skill carries the
full loop."*

## 4. Provider checklist

1. Standalone AIPP with the 4 core endpoints; register via the registry install.
2. Namespaced, stable tool names (`{domain}_*`), `visibility` including `llm`.
3. One `prompt_contributions[layer=ambient_prompt]` describing *when* to use the
   capability across all sessions.
4. Optional `lifecycle: pre_turn`/`post_turn` only if the Host should invoke the
   capability automatically (memory does; outline does not).
5. Consumers declare `requires: ["{domain}_*tool names*"]` and depend on names,
   not on your `app_id`.

## 5. What this is NOT

- Not a new manifest "type" — `GET /api/app` has no `provider` flag.
- Not a private channel — providers are reached through the Host like any tool.
- Not a language/DSL framework — a "language provider" (e.g. outline-one) is just
  a capability provider whose tools happen to parse/infer/interpret a language.
