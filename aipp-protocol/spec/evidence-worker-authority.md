# Passive evidence worker authority

Version 1. This optional service contract is separate from AIPP tool discovery and user
login validation. Implemented structurally by `EvidenceWorkerProtocol` in aipp-protocol;
cryptography, key loading, bounded HTTP and replay persistence reuse aipp-invocation-java.
See [passive evidence](passive-evidence.md) for source/recipient and receipt semantics.

## Independent authority boundaries

A local Host subscription is not backend consent. A workload key authenticates its
configured issuer/subject, not an arbitrary user in a request. Consent creation and
renewal require a fresh login session owned by the user. No service credential, model
argument, retained bearer, missing catalog, wildcard function grant or unregistered
operation may establish unattended consent.

Consent binds workload issuer/subject, canonical user UUID, agent, exact execution scope,
recipient app/origin, Host subscription ID/revision, acceptance/removal operations and
organization. Current account existence, consent and function/app grants are checked on
every worker attempt. The current 12th account model has no separate suspended-account
state; deleted accounts and missing memberships/grants fail. If account status is added,
the worker gate must consume it too.

Function decisions preserve the authority's existing app-gate semantics: an exact
registered evidence **tool** is required, and a grant for that tool or an applicable app
gate must exist. An app gate cannot make an absent evidence operation registered.
No interactive catalog/allow cache supplies a worker decision.

## Owner-managed consent

`POST /api/evidence/delegations` accepts an object with exactly these fields:

| Field | Meaning |
| --- | --- |
| `schema` | `aipp.evidence-delegation/v1` |
| `issuer`, `workload` | Configured Host workload issuer and proof subject |
| `agent_id`, `execution_scope` | Exact passive-evidence scope; no wildcards |
| `recipient_app`, `recipient_origin` | Exact app ID and origin |
| `subscription_id`, `subscription_revision` | Canonical scope digest and positive Host revision |
| `accept_operation`, `remove_operation` | Distinct exact server tool names |
| `organization` | Organization whose current function/app grants apply |
| `expires_at` | UTC instant, at least 90 seconds and at most 30 days from consent |
| `expected_revision` | 0 creates; a current positive backend revision renews |

The owner is obtained from the login session; there is no body user-ID override. Consent
is at most 8,192 UTF-8 bytes. All unknown fields, duplicate members, trailing values,
invalid scalar types and oversized/deep structures are rejected.

`subscription_id` is SHA-256 of canonical JSON
`[user, agent, canonicalExecutionScopeString, recipientApp, recipientOrigin]`.
`delegation_id` is SHA-256 of canonical JSON
`[user, issuer, workload, subscriptionId, subscriptionRevision]`.
These digests are identifiers, not credentials.

The backend scope is immutable. A matching optimistic revision permits updating expiry
and advances backend revision. Widening needs new explicit user consent under the
corresponding Host subscription revision. A revoked scope cannot be renewed/resurrected.
Expired consent can be renewed by the user if it was not revoked. Renewal of short-lived
worker decisions never extends the consent's expiry.

`DELETE /api/evidence/delegations` accepts exactly `delegation_id` and positive
`expected_revision`; it requires the owner's live login session. Revoke stops acceptance
and cleanup decisions. A repeated revoke at the current revoked revision is idempotent.
Scope updates, renewal and revoke serialize on the delegation row.

`GET /api/evidence/delegations` lists only the authenticated owner's scopes and current
revision/expiry/revocation metadata. Responses are at most 16,384 bytes and 100 entries,
with `delegations` and `next_after`. A nonempty `next_after` is followed using exactly
`?after=<64 lowercase hex characters>`; an empty value ends pagination. The order is
stable by delegation ID. Concurrent additions/revocations require refreshing the list;
this is not a frozen cross-page snapshot. No other query is supported.

Create/renew/revoke return `delegation_id`, `revision`, `expires_at`, `revoked`. An expired
or revoked login cannot manage consent. This API supplies no consent UI or automatic
production subscription.

## Authenticated worker check

`POST /api/evidence/authorize` has no query. Use the existing invocation proof header
with `verified-request-v1`, configured workload issuer and workload subject, **no
organization claim**, audience `twelfth-users`, operation `evidence_worker_authorize`,
method POST and exact request target/body. Verification consumes the one-use proof in a
durable replay store before any business transaction. A business rollback cannot erase it.

The check body is canonical JSON, at most 16,384 UTF-8 bytes, with exactly:

```
schema: aipp.evidence-worker-check/v1
workload: configured workload subject
attempt_id: fresh canonical UUID
issuer: configured Host workload issuer
subject: source owner's canonical UUID
origin: recipient origin
subscription_id: scope digest
subscription_revision: positive Host revision
removal: boolean
source: canonical passive-evidence source metadata encoded as a JSON STRING
audience: recipient app
operation: exact acceptance or removal tool name
method: POST
target: /api/tools/<operation>
body_sha256: SHA-256 of the final recipient request bytes
```

Source metadata has the exact passive-evidence shape (including execution scope) and
matches the body owner. It contains no evidence events or transcript. The scope digest
is recomputed. The Host derives these values from retained/frozen source records; neither
an arbitrary user input nor the workload proof alone establishes provenance.

The Host binding hash is SHA-256 of canonical check JSON after removing `schema` and
`workload`. This preserves the internal Host `Request.bindingHash` contract. The full
check hash covers all canonical wire bytes, including those two fields.

## Authenticated decision

Only a fully authorized decision returns 200. The body has exactly:

- `schema: aipp.evidence-worker-decision/v1`
- `binding_sha256`, `request_sha256`, `delegation_id`
- positive `delegation_revision`, `subscription_revision`
- `checked_at`, `expires_at` (exactly 90 seconds after check)
- `organization`

The consent must still cover the complete decision lifetime. `X-Aipp-Authority-Evidence`
contains a distinct `verified-request-v1` response proof from a pinned backend issuer/key.
Its subject is the user, organization matches the decision, audience is `world-one`,
operation `evidence_worker_decision`, method POST, target `/api/evidence/decision`, and
body hash covers the exact response bytes. That target is a signature domain, not a
published route. Do not send these proofs to normal AIPP tool handlers.

The response proof retains the shared 60-second lifetime. Host consumes it once with a
durable replay store and checks the exact full request/binding/subscription/delegation
identities, subject and organization. It requires check time within five seconds of now
and no earlier than five seconds before the attempt, and at least 60 seconds of decision
validity left. A permit is not cached or reused across attempts. Host rechecks its local
source/subscription/recipient policy before issuing the final recipient proof.

Invalid/unavailable/partial/unsigned decisions cannot authorize work. Backend service
responses use 400 malformed, 401 missing/invalid login, 403 denied workload/delegation,
409 owner revision conflict, 413 oversized, 503 unavailable. These responses never imply
receipt acceptance or completed cleanup.

## Transport, lifecycle and deployment

user-one is an opaque bridge for these exact routes. It forwards unchanged body bytes
and only the relevant login or workload header, preserving the backend response proof.
It cannot synthesize an allow. Configured origins use HTTPS, with numeric loopback HTTP
allowed for local services; credentials, paths, query and fragment in configured origins
are rejected. Redirects are disabled. Requests/responses have bounded sizes and complete
response deadlines (at most ten seconds). Host pins authority verification keys and never
accepts network key discovery. No event text, private keys, proofs or consent context is
written to normal authority logs.

Private keys are operator-provisioned PKCS#8 DER files, absolute regular nonsymlink paths,
owner-read with optional owner-write only, RSA at least 2048 bits. Public trust is pinned
X.509 PEM RSA. No key is generated at production startup. The current property wiring
pins one active key per role; rolling changes fail closed until peers trust the new key.
Constructed verifiers support explicit trust maps. Key rotation under the same issuer
preserves consent; issuer replacement needs new consent and source reconciliation.

Expired invocation replay records are purged in bounded batches using database time;
live records cannot be removed by that maintenance. Consent rows remain available for
owner review/revocation and are removed if the owning account is deleted. Local
subscription withdrawal alone does not revoke backend cleanup consent. Deleting a
source can still require cleanup through its original issuer/revision. Backend expiry,
revocation or grant removal denies cleanup and leaves a pending removal obligation.

Already-issued recipient proofs keep their existing 60-second lifetime. This protocol
makes no claim of atomic distributed revocation after issuance. Automatic delivery,
receipt/removal transport, consent UI and memory consolidation are separate work.
