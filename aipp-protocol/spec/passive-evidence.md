# Passive source evidence and durable acceptance

Normative Java contract: `org.twelve.aipp.evidence.PassiveEvidence`. The general
`AippAppSpec.assertValidSkillStructure` validates an optional consumer declaration.
This contract does not enable a Host sender, grant authority, or apply memories.

## Declaration and compatibility

A consumer declares the following on its atomic acceptance tool:

```json
{
  "visibility": ["host"],
  "invocation_identity": "verified-request-v1",
  "side_effect": "idempotent",
  "canvas": {"triggers": false},
  "passive_evidence": {
    "schema": "aipp.turn-evidence/v1",
    "acknowledgement": "durable_acceptance",
    "max_events": 32,
    "max_body_bytes": 262144
  }
}
```

Ordinary `name`, `description`, and `parameters` fields remain required. A passive
consumer MUST NOT also declare `lifecycle`; legacy `post_turn` discovery must not call
it as a business hook. A Host must explicitly implement this contract, select the
recipient, enforce current grants, and validate the receipt before advancing delivery.
An app may expose separate Host-only status/removal tools with the same identity
requirement. Declaration is capability discovery, not automatic subscription or readiness.

## Explicit control roles

A status/removal tool may declare `passive_evidence_control` with exactly
`{"schema":"aipp.turn-evidence/v1","operation":"status"}` or the corresponding
`"operation":"remove"`. The common `PassiveEvidence.validateConsumer` validator
checks this optional declaration through the general tool validator. Controls require
`visibility:["host"]`, `invocation_identity:"verified-request-v1"`,
`canvas.triggers:false`, and no `lifecycle`. Status has `side_effect:"none"`; removal
has `side_effect:"idempotent"`. A tool cannot declare both acceptance and control roles.

The extension is additive; it does not change envelope bytes or require old consumers
to add it until a Host selects control tools through this discovery contract. A Host
must not infer a removal role from a tool's name, description, or generic idempotent
side-effect flag. Hosts select exact registered app/origin/operation identities and
require a server execution surface before building an HTTP invocation. Capability
discovery is separate from explicit user-scoped subscription and invocation authority.

## Verified request boundary

Use the existing `InvocationRequest` and `InvocationEvidence` adapter. Issuer trust is
operator-provisioned, never discovered from request JSON or an arbitrary URL. Verify
signature, configured issuer/key, subject, recipient audience, operation, method, exact
request target, exact body hash, expiry, and durable invocation replay claim. Verification
precedes the inbox transaction; a failed business transaction cannot erase replay protection.

Every retry requires a fresh one-use invocation proof. The delivery ID remains unchanged.
LLM delegation tokens, login bearer text inside an envelope, and `_context.userId` alone
do not provide invocation authority. The trusted Host must enforce function authorization
and derive context from its execution state before signing the final bytes. A valid
signature does not authorize arbitrary provider business operations or establish that
an attempted action succeeded.

## Request envelope

The JSON root has exactly `_context` and `evidence`.

| Object | Required fields |
| --- | --- |
| `_context` | `userId`, `agentId`, `sessionId`, `uiSessionId`, `executionScope` |
| `evidence` | `schema`, `recipient`, `source`, `intent`, `page`, `delivery_id`, `events` |
| `recipient` | `app_id`, `origin` (configured HTTP(S) origin, no credentials/path/query/fragment) |
| `source` | `user_id`, `agent_id`, `session_id`, `ui_session_id`, `turn_id`, positive `turn_sequence`, `execution_scope`, RFC3339 `expires_at`, `outcome` |
| `execution_scope` | `contextKind`, `taskKind`, `workspaceId`, `workspaceOwnerAppId` |
| `intent` | UUID `id`, `kind`, `from_ordinal`, `through_ordinal` |
| `page` | `from_ordinal`, `through_ordinal` |
| event | UUID `id`, `ordinal`, `kind`, `payload`; optional `source_links` for input/steering |

The corresponding context and source fields MUST be identical. Source user must match
verified subject. Agent/session/UI/scope metadata is immutable for an issuer-owned source
turn. Do not derive trusted scope from tool arguments or use mismatching fields as fallback.
Subject/turn/intent/delivery/event UUID strings are canonical. Outcome is `complete`,
`failed`, `cancelled`, or `awaiting_user`; it describes a turn, not verified external effects.

`terminal` intents start at 1 and end with their terminal event. `late_observation`
intents cover one later ordinal and cannot contain a terminal event. Ordinals are 1–4096.
Pages are contiguous, contain exactly their declared ordinals, have at most 32 events,
and lie within the intent range. Receiving a later page does not cover missing earlier
pages. Accepting a late range does not advance a session-wide application watermark.

Each event payload is at most 65,536 characters. Total raw request bytes are capped at
262,144; a sender must split before signing. JSON duplicate members, trailing values,
unknown envelope fields, excessive nesting/container/string sizes, and unredacted known
credential/reasoning fields are rejected. This is not a universal secret detector for
arbitrary prose. The Host remains responsible for bounded source redaction and provenance.

## Stable identity and duplicates

`PassiveEvidence.deliveryId` defines the UUID from canonical recipient, source identifiers,
intent ID, and page bounds. Freeze page boundaries and payload before the first send.
`content_sha256` is computed independently over the canonical `evidence` object. Object
key order is ignored; array order and content are retained. The transport signature hashes
exact raw bytes, a separate purpose from logical content comparison.

Consumers namespace storage by verified issuer and subject. An exact repeated delivery
returns the original receipt ID and acceptance time. Changed content under the same ID,
changed source/intent metadata, or overlapping ranges/pages must conflict atomically.
Do not silently overwrite a prior receipt, generate a new ID to evade a conflict, or
advance applied coverage merely because all pages were received.

## Links, projection, and removal

`source_links` carries only capture-time reference metadata: version 1, relationship
`reference_only`, mode `explicit` or `implicit_candidates`, and bounded entries with
status, `candidates_truncated`, optional requested message/event/tool-call selector,
and up to three `source_event_id` pointers. It rejects copied excerpts and mutable
read-time availability fields. References and ambiguity are evidence for later
interpretation; they are not accepted solutions or verified outcomes.

Related text must remain in its separately owned source. Processing must resolve links
with fresh issuer/user/agent/session/UI/scope and expiry/removal checks, treating absent
or ambiguous targets as unavailable. A live local preview must not be serialized as a
frozen delivery payload. These rules do not automatically erase independent literal
copies made by users, tools, or transcripts; the sender must preserve source provenance
and omit historical context it cannot attribute safely.

`provider_context` payloads require an array of `{provider, origin, receipt}` matching the
recipient app/origin. Each receipt's scope must match the source context. Otherwise
preserve the ordinal with `{omitted:true, reason:"provider_projection_unavailable",
source_sha256:"<64 lowercase hex characters>"}`. A provider still validates its own
receipt references before processing; accepted transport is not proof of a valid memory.

Status/removal requests use the same root/context and `schema`, `recipient`, `source`,
with no intent/page/events/delivery ID. Retention and removal are consumer-enforced.
Remove stored source payloads and retain a barrier against delayed replay, including
removal arriving before first acceptance. References cannot resurrect removed text.
Host revocation delivery and downstream derivative/index invalidation require their own
durable integration; declaring this contract alone does not supply them.

## Acknowledgement

Return HTTP 202 and an acceptance receipt only after commit, with schema `aipp.evidence-receipt/v1`,
`delivery_id`, `receipt_id`, `content_sha256`, `accepted_at`, `status:"accepted"`,
`processing_state` (`receiving` for incomplete range, `pending` when complete), and
`applied:false`. Duplicates retain receipt identity/time; processing state may reflect
later accepted pages. The sender validates all immutable receipt fields against its
persisted page before considering that page delivered. `PassiveEvidence.receipt` validates
the exact bounded receipt structure, canonical UUIDs, expected delivery/content hash,
acceptance state, timestamp, and `applied:false`. This parser does not authenticate a
response: the Host must bind it to the trusted recipient and the exact frozen request
through its transport. Unknown fields, duplicate members, trailing values, and receipts
larger than 4,096 bytes are rejected. Receipt identity/time cannot change on replay;
`processing_state` may progress as remaining pages arrive. Host clock-skew checks and
receipt reconciliation persistence are Host responsibilities.

400 rejects malformed data; 401 rejects invocation identity; 409 denotes an immutable
identity/content/range conflict; 410 denotes removed/expired source; 413 denotes bounds;
503 means trust/storage is unavailable. None is an acceptance receipt. Retrying a
transient failure uses bounded backoff and a fresh invocation signature; conflict and
removal require explicit reconciliation, never blind replay of external actions.

## Removal acknowledgement

After committing source payload erasure and the permanent removal barrier, return HTTP
200 with a bounded `aipp.evidence-removal-receipt/v1` object. Its exact fields are
`schema`, `removal_id`, `receipt_id`, `removed_at`, `issuer`, `recipient` (`app_id`,
`origin`), `source_sha256`, `request_sha256`, `source_state:"removed"`, and `applied:false`.
`EvidenceRemovalReceipt` defines and validates the canonical form. `removal_id` is a
name UUID over canonical `[schema, issuer, recipient, source.canonicalJson()]`, where the
last element is the canonical source JSON string; `source_sha256` hashes
canonical source metadata and `request_sha256` hashes the exact request bytes.

Persist a canonical receipt UUID and first removal time in the same transaction as
removal. Repeating the same logical removal MUST retain that receipt identity/time,
including across process restarts. The response request hash binds the particular
request bytes; changing only JSON whitespace cannot change the persisted removal
receipt identity. The sender rejects extra/duplicate fields, trailing data, noncanonical
IDs, mismatched binding, invalid timestamps, and responses larger than 4,096 bytes.
The sender enforces clock skew and immutability when reconciling its source row.

This is a structural acknowledgement, authenticated by the selected HTTPS recipient
transport (numeric-loopback HTTP is permitted for local services). It is not a separate
signed authority decision. A timeout, error or unvalidated reply cannot mark cleanup
complete. Retry the original control with a fresh invocation proof. An exact valid late
reply may reconcile completion after its sender lease expires; it cannot change a source
or clear a removal barrier. Completion is retained independently of journal expiry, and
repeated local deletion must not reopen completed cleanup. `applied:false` does not claim
that any memory consolidation occurred or that future derivative invalidation exists.

## Unattended worker authority

Fresh backend workload checks, owner-managed consent and signed decisions are specified
in [evidence-worker-authority.md](evidence-worker-authority.md). A passive source envelope,
local subscription or valid Host signing key alone never establishes that consent.
