# Entitir capability — build a data application on an ontology world

> Status: contract v1 (2026-11), **doc-first**. The names below are the contract. §9 lists which of them a deployed provider serves today and which earlier name reaches the same operation until it does.
> Provider: `world-entitir` (a normal registered AIPP) · Pattern: [`capability-providers.md`](capability-providers.md)
> Java: `org.twelve.aipp.invoke.EntitirTools` (names + `requires` bundles), `EntitirCapabilitySpec` (provider gate)

An AIPP that needs structured data — typed entities, relations, rules that act on them, and analysis over them — does not build its own store. It uses an **ontology world** through this capability: create a world, define its types, build and release it, read and change its rows, run its decisions and actions, and publish analysis topics.

The consumer depends on **tool names** only. It holds the Host URL, never the provider's app id or base URL, and the Host has no knowledge of this capability beyond routing tool names.

---

## 1. How a consumer depends on it

1. Declare the bundles you use at your `/api/tools` root:

   ```json
   { "app": "my-app", "version": "1.0",
     "requires": ["entitir_data_schema", "entitir_data_eval", "entitir_decision_run"],
     "tools": [] }
   ```

   In Java: `EntitirTools.requires(EntitirTools.DATA_READ_REQUIRES, EntitirTools.RUN_REQUIRES)`.

2. From your **model**: list the names in a skill's `allowed-tools`. The model calls them like any tool; the Host routes by name.

3. From your **backend**: call through the Host proxy with the shared client.

   ```java
   HostToolClient host = HostToolClient.resolving(fallbackHostUrl, "my-app", callbackToken);
   JsonNode out = host.call(EntitirTools.DATA_EVAL, Map.of(
           "world_id", worldId,
           "expression", "orders.filter(o -> o.status == \"OPEN\").count()",
           "_context", Map.of("userId", userId)));
   if (out == null || !out.path("ok").asBoolean(false)) {
       // capability not installed, provider down, or the call was refused
   }
   ```

   A backend call may invoke only names in your `requires` ([`capability-providers.md`](capability-providers.md) §3).

4. Degrade explicitly. An unresolved name means the capability is not installed or the provider does not serve that name yet (§9). Tell the user; do not fall back to a direct URL.

| Bundle (`EntitirTools`) | Names | Use it to |
|---|---|---|
| `WORLD_REQUIRES` | `entitir_world_list` `_get` `_create` `_update` `_delete` | Find, create, rename and delete worlds |
| `MODEL_REQUIRES` | `entitir_model_get` `entitir_model_apply` `entitir_world_build` `entitir_world_release` | Change types, build staging, release to production |
| `DATA_READ_REQUIRES` | `entitir_data_schema` `_search` `_neighborhood` `_eval` `_ask` | Walk through a built world |
| `DATA_WRITE_REQUIRES` | `entitir_data_change` | Create, update and delete entity rows |
| `CAPABILITY_REQUIRES` | `entitir_capability_list` `_save` `_remove` | Author decisions and actions |
| `RUN_REQUIRES` | `entitir_capability_list` `entitir_decision_run` `entitir_action_run` `entitir_execution_list` `_get` | Run decisions and actions, read what a run did |
| `TOPIC_REQUIRES` | `entitir_topic_list` `_save` `_delete` `_publish` | Author and publish analysis topics |

---

## 2. Rules common to every tool

| Rule | Detail |
|---|---|
| **Addressing** | A world is addressed by `world_id`. No contract tool takes a `session_id`, opens a canvas, or needs the World Designer to be open. |
| **Environment** | `env` is `staging` or `production`; omitted means `production`. Model tools (§4) act on the world's draft and take no `env`. |
| **Identity** | The acting user is `_context.userId`. The Host injects it for model calls; a backend passes it as shown in §1. A call without a user is refused. |
| **Result** | Success is `{ "ok": true, "world_id": "…", … }`. Failure is `{ "ok": false, "error": "<code>", "message": "…" }`. Branch on `ok`, not on the HTTP status. |
| **Error codes** | `invalid_args`, `world_not_found`, `forbidden`, `not_built` (no release in that `env`), `build_failed`, `run_failed` (a run reached the engine and failed, including a decision it started), `needs_confirmation`, `conflict`, `unavailable`. |
| **Destructive calls** | Deleting a world, removing a type or field, and removing a decision or action require `confirmed: true`. Without it the tool changes nothing and returns `needs_confirmation`. Ask the user first; never set it on your own. |
| **Retries** | Reads are `side_effect: none`. Run and change tools accept `idempotency_key`; a repeat with the same key returns the first result and does not run again. This holds for a failed run too. A `need_input` result ran nothing, so the completed call may reuse the key. The same key with different arguments is refused with `conflict`. |
| **No UI in results** | Results are data. A contract tool never returns a canvas or widget envelope. |

---

## 3. Worlds

| Tool | Args | Result |
|---|---|---|
| `entitir_world_list` | `{env?, include_archived?}` | `worlds: [{world_id, name, archived, staging_version, production_version, description}]` — only worlds the user may access. With `env`, only worlds released to it. An archived row has this same shape. |
| `entitir_world_get` | `{world_id}` | The same row plus `entity_count`, `description`, `last_build_error`. |
| `entitir_world_create` | `{name, world_id?, description?}` | `{world_id, created}`. With a caller-chosen `world_id` the call is an ensure: an existing world is returned with `created: false`. A `world_id` longer than 36 characters is `invalid_args` and nothing is saved. |
| `entitir_world_update` | `{world_id, name?, description?}` | The updated row. |
| `entitir_world_delete` | `{world_id, confirmed}` | `{deleted: true}`. A soft delete: the world leaves every list and stops accepting calls; its data is kept and the user can restore it in the provider's own UI. |

A new world is empty. It accepts data only after §4 has given it types and a build.

## 4. Model, build, release

| Tool | Args | Result |
|---|---|---|
| `entitir_model_get` | `{world_id, class?}` | `definitions: [ … ]` — the draft's entity, ontology and enum definitions in authoring JSON; `dirty` is true when the draft differs from the last build. |
| `entitir_model_apply` | `{world_id, upsert?: [definition], remove?: [class], confirmed?, build?}` | `{applied: [...], removed: [...], build?}`. `upsert` adds a class or replaces it whole. `remove`, and an `upsert` that drops a field, need `confirmed: true`. `build: true` runs `entitir_world_build` afterwards; when that build fails the result is the build failure below, with `applied` and `removed` added. |
| `entitir_world_build` | `{world_id}` | `build: {status, release_version}`. Compiles the draft into `staging`. `status` is `built`, or `unchanged` when the provider finds nothing new to compile; a caller treats both as a usable `staging`. A failed build is `ok: false`, `error: build_failed`, with `build: {status: "build_failed", build_errors: [...]}`: fix what `build_errors` names and build again. |
| `entitir_world_release` | `{world_id, confirmed?, confidence?, threshold?}` | `{promoted}`. Promotes `staging` to `production`. When `confidence` is below `threshold` (default 0.8) and `confirmed` is not true, nothing is promoted and `needs_confirmation: true` is returned. A stored decision or action that fails its type check blocks the release: the result is `ok: false` with `promoted: false` and `failures` naming each one. |

Order: `entitir_world_create` → `entitir_model_apply` → `entitir_world_build` → write and check data in `staging` → `entitir_world_release`. Content changes (§5) apply at once; structure changes reach `production` only through build and release.

The authoring JSON for a definition is taught by the provider's `entitir_data_app` skill (§8), not repeated here.

## 5. Data

| Tool | Args | Result |
|---|---|---|
| `entitir_data_schema` | `{world_id, env?}` | The built model: domains, entity / ontology / enum types with fields, inheritance and identity rules. |
| `entitir_data_search` | `{world_id, query, domain_id?, limit?, env?}` | Type names and definitions matching `query` (at most 50). For worlds too large to read whole. |
| `entitir_data_neighborhood` | `{world_id, domain_id, class, depth?, env?}` | The type graph 1–3 hops around one class. |
| `entitir_data_eval` | `{world_id, expression, limit?, env?}` | The value of one read-only Outline / VirtualSet expression: filter, join, aggregate, compare. It cannot write. |
| `entitir_data_ask` | `{world_id, question, env?}` | `{expression, answer, evidence, execution_id}` — the provider writes the expression for a natural-language question, proves it read-only and runs it once. |
| `entitir_data_change` | `{world_id, changes: [{operation, entity_type, fields, client_key?}], stop_on_error?, idempotency_key?, env?}` | `{batch_id, items, client_keys}`. `operation` is `create`, `update` or `delete`. Changes run in order; a later change refers to a row created earlier in the batch with `"$ref:<client_key>"` in a foreign-key field. `stop_on_error` defaults to true. |

Read with `entitir_data_schema` first, then `entitir_data_eval`. Use `entitir_data_ask` when the question is the user's own words and no expression is at hand. Write Outline with the Outline capability (`outline_*`) rather than by hand.

A change is an event on the world: decisions that watch the changed type may run because of it.

## 6. Decisions and actions

An **action** is one atomic operation on an entity type. A **decision** is a rule that checks a trigger and orchestrates actions. Both are listed as capabilities of a world.

| Tool | Args | Result |
|---|---|---|
| `entitir_capability_list` | `{world_id, kind?, detail?, env?}` | `capabilities: [{kind, id, name, entity_type?, description, parameters}]`. `kind` is `decision` or `action`. `detail: true` adds the full definition for editing. With `env`, the decisions and actions of that release; without `env`, the draft. |
| `entitir_capability_save` | `{world_id, kind, definition}` | `{id, compile_ok, errors?}`. Creates the decision or action, or replaces the one with the same id. It is saved to the draft; build and release (§4) make it runnable. A decision that fails the stored check is still saved and returns `compile_ok: false` with `errors`. |
| `entitir_capability_remove` | `{world_id, kind, id, entity_type?, confirmed}` | `{removed: true}`. `id` may be `Type::name`. `entity_type` is required only when more than one type has that action name; the refusal lists those types. |
| `entitir_decision_run` | `{world_id, template_id, parameters?, idempotency_key?, scope_id?, env?}` | `{status, execution_id, …}`. `status` is `ok`, `accepted` (still running), `need_input` (with `missing_params`), `rejected`, or `hard_failed`. `rejected` is `ok: true`: the trigger said no, and `reason` says why. `hard_failed` is `ok: false`, `error: run_failed`, with `status`, `message` and `execution_id`. A run also fails when a decision it started fails; `failed_template_id` then names that decision. |
| `entitir_action_run` | `{world_id, entity_type, action, entity_id?, parameters?, idempotency_key?, env?}` | `{status, execution_id, result?}` with the same `status` values. |
| `entitir_execution_list` | `{world_id, limit?, offset?, env?}` | Runs newest first. |
| `entitir_execution_get` | `{execution_id, world_id?}` | One run: steps with inputs and outputs, `error_code`, `error_message`, and a diagnosis. |

On `need_input`, collect the values named in `missing_params` from the user and call again with them. Do not invent values.

A released decision may also be published by the provider as its own skill. That is the route for end users in chat; the tools above are the route for an app that drives a world.

## 7. Analysis topics

A topic is a named analysis brief on a world: a title, a markdown brief, the ontology type it concerns, an optional trigger, and the users it belongs to. Publishing a topic makes its current version available to those users.

| Tool | Args | Result |
|---|---|---|
| `entitir_topic_list` | `{world_id, id?}` | `topics: [{id, title, context, ontology_type, trigger, user_ids, version, can_publish}]`. With `id`, that topic only. |
| `entitir_topic_save` | `{world_id, title, id?, context?, ontology_type?, trigger?, user_ids?}` | `{topic}`. Without `id` a topic is created; with `id` it is updated and omitted fields keep their value. A change to title, brief, ontology or trigger raises `version`. |
| `entitir_topic_delete` | `{world_id, id}` | `{deleted: true}`. |
| `entitir_topic_publish` | `{world_id, id}` | `{topic}`. Fails with `conflict` when this version is already published. Check `can_publish` first. |

Only a topic's users may change, delete or publish it.

---

## 8. Provider rules

A provider of this capability is an ordinary AIPP ([`capability-providers.md`](capability-providers.md) §4) that also:

| Rule | Detail |
|---|---|
| **Placement** | Every contract tool has `visibility` including `llm`, **no** `owner_widget`, and no `session_id` parameter ([`field-semantics.md`](field-semantics.md) §3). Tools that need an open canvas keep their own names outside this contract. |
| **`side_effect`** | `none` on reads; `idempotent` or `mutating` on the names in `EntitirTools.MUTATING`. |
| **One ambient prompt** | One `ambient_prompt`, at most two sentences, pointing at the skill below. Guidance for the provider's own canvas goes in its `entry_prompt` and skills. |
| **Consumer skill** | Ship an app-level skill `entitir_data_app` whose `allowed-tools` are contract names. It carries what this spec leaves out: the definition authoring JSON, the Outline patterns for `entitir_data_eval`, and the create → model → build → release → data procedure. |
| **Access** | Scope every call to `_context.userId`: list only what that user may access, refuse the rest with `forbidden`. v1 has no per-app boundary — a provider receives no verified identity of the calling app. |
| **Earlier names** | Keep serving the names in `EntitirTools.EARLIER_NAMES` with their existing response shape. |
| **Gate** | `EntitirCapabilitySpec.assertValidProviderTools(tools)` on the `/api/tools` response, and `assertProvides(tools, bundle)` for each bundle the provider claims. |

The prefix `entitir_` may also carry provider tools that are not in `EntitirTools.ALL` (for example canvas guides and traces). They are not part of this contract and a consumer must not depend on them.

**Change notifications are not part of v1.** The only push today is `ontology_subscribe`, which takes a direct `callback_url` ([`ontology-world-capability.md`](ontology-world-capability.md) §2). Do not poll a world to detect changes.

---

## 9. What resolves today

Provider status as of 2026-11-20: world-entitir serves all 26 contract names (and two widget-only tools, `entitir_world_claim` and `entitir_world_share`), including `run_failed`, `rejected`, release listing, action ids of the form `Type::name`, and the 36-character `world_id` limit. It is not deployed. It was run with entitir-server, the mode the deployed provider uses, and answered the calls in `world-entitir/scripts/test-entitir-capability-remote.py` (48 checks). The deployed provider serves **none** of them. A consumer that must run against the deployed provider calls the earlier name in the middle column. `EntitirTools.EARLIER_NAMES` holds the rows marked *same operation*.

The ontology-rag working tree calls the data contract names (`entitir_data_schema`, `entitir_data_search`, `entitir_data_neighborhood`, `entitir_data_eval`, `entitir_data_ask`, `entitir_data_change`) and passes the user in `_context`. Deploy world-entitir before ontology-rag; otherwise those calls do not resolve. decision-reactor still calls `ontology_list_worlds`, `ontology_entry_templates`, `ontology_entity_outlines`, and `ontology_subscribe`.

| Contract name | Earlier name served today | Difference |
|---|---|---|
| `entitir_world_list` | `ontology_list_worlds` | Released worlds only; backend calls only |
| `entitir_world_get` | — | |
| `entitir_world_create` | `wiki_ensure` | Caller must supply `world_id`; backend calls only |
| `entitir_world_update` | — | |
| `entitir_world_delete` | — | |
| `entitir_model_get` | — | `wiki_schema` returns the built types, not the draft |
| `entitir_model_apply` | `wiki_add_types` | Adds or replaces; no removal; backend calls only |
| `entitir_world_build` | `wiki_build` | Same operation; backend calls only |
| `entitir_world_release` | `wiki_promote` | Same operation; backend calls only |
| `entitir_data_schema` | `analysis_world_schema` | Same operation |
| `entitir_data_search` | `analysis_world_search` | Same operation |
| `entitir_data_neighborhood` | `analysis_world_neighborhood` | Same operation |
| `entitir_data_eval` | `analysis_world_eval` | Same operation |
| `entitir_data_ask` | `ontology_data_query` | Same operation |
| `entitir_data_change` | `world_entity_change_batch` | Same operation; the model sees it only in the designer canvas |
| `entitir_capability_list` | `ontology_entry_templates` | Entry decisions only; backend calls only |
| `entitir_capability_save` | — | |
| `entitir_capability_remove` | — | |
| `entitir_decision_run` | `invoke_decision` | Same operation |
| `entitir_action_run` | — | |
| `entitir_execution_list` | `runtime_execution_list` | Same operation |
| `entitir_execution_get` | `runtime_execution_get` | Same operation |
| `entitir_topic_list` `_save` `_delete` `_publish` | `analysis_topic_list` `_save` `_delete` `_publish` | Same operation with `world_id`; the model sees them only in the designer canvas |

"Backend calls only" means the tool is `visibility: ["host"]`: reachable through the Host proxy, not offered to a model. Earlier names return their own response shapes, which do not all follow §2.

Access in the current world-entitir source is enforced on the `entitir_*` names: a world records its creating user, a world with no row stays open to every signed-in user, and anyone else gets `forbidden`. The deployed provider still has no owner until that source is deployed. Earlier names do not check. decision-reactor and the designer still call those names, so the check stays off them until the reactor moves. A world with no owner row remains open on either name.

---

## 10. Relation to the ontology-world capability

[`ontology-world-capability.md`](ontology-world-capability.md) defines the `wiki_*` and `ontology_*` names used by the wiki and decision-reactor consumers. Those names stay valid. This capability is the general surface for any data application; new consumers use it and reach for a `wiki_*` or `ontology_*` name only where §9 points to one.

**Notifications.** The contract has no subscribe tool. `POST /api/aipp/runtime-events/forward` is not admitted for AIPP callback credentials (`WorldOneAuthFilter.isTrustedAippHostCallback` lists proxy tools, schedules, notifications, memory and session paths only), so a provider cannot push through the Host today. Consumers that need change pushes keep `ontology_subscribe` with a direct `callback_url`. Admitting the forward route for AIPP callers is a generic Host change, not yet made.
