# Verified invocation identity — implementation groundwork

Status: shared validation/SDK groundwork, not activated Host/app integration. No Host or app may
advertise support based solely on these constants. Trust provisioning and all dispatch/consumer
paths must be tested before enabling a tool requirement in a deployed manifest.

Contract: `org.twelve.aipp.identity.AippInvocationIdentityContract`.

Implementation checkpoint: the generic sibling `aipp-invocation-java` SDK now implements an
RS256 compact-JWS profile using Nimbus, pinned public keys and a mandatory replay-store interface.
Its README defines signature, lifetime and key-management requirements. This does not activate
Host or app support: durable replay storage, manifest negotiation and deployment trust remain open.

Request-binding primitive: `InvocationRequest` snapshots final body bytes and exact origin-form
target/query. Its body accessor returns a copy and its diagnostic string is redacted. SHA-256 is
lowercase hexadecimal over exact bytes, including whitespace; no JSON reserialization is allowed
between issuing and verifying evidence. Current construction rejects dot segments, encoded path
separators, double-encoded percent paths, fragments, absolute origins and non-ASCII raw targets.
It does not authenticate identity or verify evidence. Hosts must construct it only after argument
enrichment; consumers must use received bytes and their configured audience/operation, not claims
provided by the caller. End-to-end proxy conformance remains required before activation.

## Dependency boundary

Hosts and apps depend on shared; shared never depends on either. App service layers depend on
their domain modules. Hosts route discovered operations generically and do not import app
domain classes or interpret app authorization rules. Shared contains wire names, not application
permissions, domain states, widget implementations, keys or provider URLs.

## Requirement and evidence

The reserved tool requirement is `invocation_identity: verified-request-v1`. A supporting Host
must verify the active principal, enforce its existing function dispatch gate, and attach
independently verifiable request evidence through `X-Aipp-Invocation-Identity`. An unsupported
Host must reject the invocation rather than silently downgrade to body/header identity labels.
Consumers must independently enforce the requirement regardless of Host discovery filtering.

AippAppSpec.assertValidSkillStructure invokes assertValidInvocationIdentity. Absence preserves
legacy behavior; an explicit field must be exactly the supported string. Null, boolean, object,
blank or unknown versions fail validation. This field is independent of visibility, side_effect,
requires_authority and app resource permissions; none of those imply another.

The generic SDK InvocationTransport helper rejects a protected invocation if its requirement or
issuer is unavailable. It prepares a redacted request wrapper with immutable evidence headers.
Host adapters must use the registered manifest (never caller arguments), strip caller-supplied
identity headers, finish argument enrichment first and send precisely the prepared snapshot.
The helper does not itself send HTTP, verify login identity or enforce function permissions.
Older Hosts that ignore this field are not safe deployment targets for protected tools.

The logical evidence fields cover version, issuer, subject, optional organization, recipient
audience, unique invocation ID, issuance/expiry, operation, HTTP method, exact app-local request
target (including query) and a SHA-256 digest of the exact forwarded body bytes. These are logical
names, not a claim that unsigned JSON is acceptable evidence. Select a supported verification
profile and library; do not implement a custom signature scheme inside this contract module.

Argument enrichment must finish before binding. Unknown versions, recipient/operation/request
mismatches, expiry, absent trust and unavailable verification fail closed. A consumer derives
its principal only from verified evidence and retains its own data/resource permission checks.
Host function permission is not an app-level data grant.

For mutation, durable atomic replay claims must bind issuer, audience, invocation ID and request
digest. A retry must not repeat the mutation; ambiguous execution requires a defined result/status
protocol before retrying. Evidence is not a reusable login credential, a domain approval, or a
substitute for current resource state checks. Do not forward the user's login bearer to installed
apps to implement this feature.

## Before activation

- Specify evidence encoding/algorithm profile, trust/key lifecycle, timestamps and clock tolerance.
- Define exact request-target construction and query handling across proxies without ambiguity.
- Implement issuer and verifier adapters, durable replay claims and safe failure semantics.
- Test two unrelated synthetic apps to prove generic behavior, plus wrong audience, changed body,
  operation mismatch, expiry, unknown versions, replay and verification outage.
- Keep evidence out of model context, widget payloads, browser storage, logs and audit messages.
- Extend manifest validators and capability negotiation before any app declares the requirement.

Names-only tests intentionally do not certify authentication security. Existing `get_user` and
function-authority contracts are unchanged. Widget transport and CSS remain governed by the
existing shared widget contracts; no new app-specific Host API is introduced.
