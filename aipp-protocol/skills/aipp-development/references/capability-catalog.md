# Shared capability catalog (index)

> Thin index for coding agents. Normative detail: [`../../../spec/capability-providers.md`](../../../spec/capability-providers.md).
> Depend on **tool names**, never on a provider `app_id` or hardcoded `base_url`. Cross-AIPP calls go through the Host.

## How to consume

1. List needed tools in skill `allowed-tools` and/or `/api/tools` root `requires: ["tool_name", …]`.
2. Call tools by name (Host routes to the owning registered AIPP).
3. Degrade gracefully if a required tool is missing (tell the user the capability is not installed).

## Providers (examples)

| Capability | Typical app | Tool namespace | When to reach for it | Spec |
|------------|-------------|----------------|----------------------|------|
| Long-term memory | memory-one | `memory_*` | Persist / recall user facts across sessions; Host may schedule `pre_turn` / `post_turn` | [`capability-providers.md`](../../../spec/capability-providers.md), [`field-semantics.md`](../../../spec/field-semantics.md) |
| Outline language | outline-one | `outline_*` | Parse / infer / complete / run Outline instead of hand-writing scripts | [`capability-providers.md`](../../../spec/capability-providers.md) |
| Contacts | chat-one | `contacts_*` | Resolve friends/coworkers visible to the caller | [`contacts.md`](../../../spec/contacts.md) |
| User profile | user-one | `user_profile_view`, `find_user`, `get_principal` | Open a readonly name card or resolve display names by canonical id | [`user-profile.md`](../../../spec/user-profile.md) |
| Decision reactor | decision-reactor | catalog REST + session push | Entry templates, ontology session changes — not a generic tool dump | [`decision-reactor-integration.md`](../../../spec/decision-reactor-integration.md) |
| Ontology / wiki | world provider (e.g. world-entitir) | `wiki_*`, `ontology_*` (Host-brokered) | Knowledge graph / wiki ops via Host proxy | [`ontology-world-capability.md`](../../../spec/ontology-world-capability.md) |

## Common tool names (illustrative)

Exact manifests live on the provider’s `GET /api/tools`. Names below are the stable global conventions agents should expect:

| Family | Examples | Notes |
|--------|----------|-------|
| Memory | `memory_query`, `memory_update`, `memory_view`, `memory_load`, `memory_consolidate` | Writes → `mutates_display`; lifecycle tools → `visibility` includes `host` |
| Outline | `outline_parse`, `outline_infer`, `outline_completion`, `outline_grammar` | On-demand; ambient prompt points at skill |
| Ontology / wiki | `wiki_*`, `ontology_*` | Prefer Host `POST /api/proxy/tools/{name}` — see ontology-world-capability |

## Anti-patterns

| Do not | Do |
|--------|-----|
| Hardcode `http://…memory-one…` in another app | Call `memory_*` through Host routing |
| `requires: ["memory-one"]` (app id) | `requires: ["memory_query", …]` (tool names) |
| Put provider manuals in `ambient_prompt` | ≤ 2 sentences + pointer to skill/tools |
