# AIPP invocation evidence SDK

Generic implementation dependency: Host -> shared SDK <- app service -> app modules.
The SDK depends only on shared protocol, Nimbus JOSE+JWT and their generic dependencies.
No Host/app names, permissions, recovery states or UI are implemented here.

## Profile v1

- Compact JWS through Nimbus JOSE+JWT 10.4.2; RS256 only, RSA keys at least 2048 bits.
- Protected header must contain exactly alg, typ (`aipp-invocation+jws`) and kid. Embedded keys,
  remote key URLs, alternate algorithms, critical extensions and unencoded payloads are rejected.
- JSON payload follows AippInvocationIdentityContract logical fields. Duplicate/unknown fields
  are rejected. Numeric times are integral UTC epoch seconds; lifetime is at most 60 seconds.
  Issuance may be at most 5 seconds ahead of verifier time. Expiry has no grace period.
- Exact recipient, operation, HTTP method, app-local target/query and final raw-body SHA-256
  must match the receiving endpoint's locally derived InvocationRequest.
- Evidence is capped at 16 KiB. Identity is returned only after signature, claims, request and
  replay checks. Errors do not include evidence or parser exception causes.

This is JWS, not a reusable login JWT. The API may issue only for a subject obtained from the
Host's verified session after its function gate. Never accept a subject argument from an LLM.
See [Nimbus documentation](https://connect2id.com/products/nimbus-jose-jwt/faq) for library/JCA details.

## Trust and operations

Issuer private keys remain in the Host/issuer secret store; do not place them in shared, app
configuration responses, widgets or logs. Each consumer receives a deployment-provisioned map
from kid to expected issuer and public key. Never resolve trust from an evidence-provided URL.
Each verifier configuration must use unique key IDs; immutable configuration snapshots are
recommended. Rotate by provisioning the new public key before switching issuance; retain an old
key only for the maximum outstanding evidence lifetime. Remove compromised trust immediately.
There is no automatic key generation, remote discovery or trust-on-first-use in this SDK.

ReplayStore is a required interface. The supplied PostgresInvocationReplayStore adapter's claim must
atomically reject the same issuer/audience/invocation ID across all consumer processes and retain
the claim until expiry (and longer if required by result retention). This profile consumes IDs
for reads and writes. Storage outage denies invocation. Repeating a failed or lost-response request
must not repeat a mutation; the app needs an explicit durable operation-status/idempotency policy.
The HashSet in signature unit tests is synthetic and is not a deployable replay store.

### PostgreSQL adapter

PostgresInvocationReplayStore uses shared AtomicDbOps and the consumer's own schema. Initialize
its fixed replay table explicitly during startup. The schema name is trusted deployment input,
not request data; system/public schemas and invalid identifiers are rejected. The consumer supplies
its JDBC driver, DataSource and transaction-enabled AtomicDbOps. No raw identity token or subject
is stored: the unique claim key hashes length-prefixed issuer/audience/invocation ID; expiry and
claim timestamps are stored alongside it. Structured SQL logs therefore do not receive the token.

Call verification before entering any business transaction. The adapter rejects an existing
transaction, creates a transaction for the unique INSERT and returns only after that transaction
commits. It also rejects transaction-disabled db-ops configuration. Concurrent connections and
reconstructed stores share the same primary-key constraint. Expiry is checked again by the database;
issuer/verifier/database clocks must be synchronized. This is one-use enforcement, not an
operation-result cache or exactly-once execution guarantee.

There is deliberately no automatic pruning yet: an existing claim is never overwritten or revived
by a new expiry. Retention is conservative/unbounded until a safe cleanup policy is implemented.
Do not manually remove live replay claims. Business failure after a successful claim does not
release the claim; a lost result still requires app-specific status/idempotency handling.

SDK verification establishes request identity, not app permission, fresh human approval, current
resource revision, or external-effect success. Apps retain their own authorization checks.

## Activation gates still open

InvocationHttpRequest builds an immutable Java HTTP JSON request from the exact signed snapshot.
Its routing input is a trusted HTTP(S) origin only (no base path, credentials, query or fragment);
the snapshot contains the complete app-local path/query. It does not normalize or resolve that
target, reserialize the body, accept inbound headers, send HTTP, or retry requests. Callers must
use a redirect-disabled HTTP client, enforce identity/function gates first, and avoid logging
request headers. A local HTTP regression verifies two synthetic app audiences, raw query/body
preservation, no bearer forwarding and replay rejection using a test-only in-memory replay store.
Production consumers require the durable replay adapter. World One's generic tool proxy now uses
this builder through an optional verified-identity adapter; signer provisioning is opt-in and
unsupported dispatch surfaces remain fail-closed. This is not deployment activation.

InvocationTransport is the generic pre-dispatch helper: it uses a registered tool's requirement,
the final immutable InvocationRequest and a trusted issuer callback. It neither accepts inbound
headers nor performs network requests. Protected calls fail closed if the issuer is absent,
unsupported, fails or produces an invalid header. Legacy calls do not issue evidence. Host code
must strip any caller-provided identity header, invoke after identity/function gates and all
argument enrichment, then send exactly the returned request. This helper is not yet called by
every Host surface. AippAppSpec validates the requirement, but deployment negotiation and
every actual dispatch path still need integration acceptance.

World One and Entitir now have opt-in adapters/configuration with local integration tests.
Durable operation-result handling, production trust/key provisioning, raw-route compatibility and
deployment acceptance remain open. Do not infer deployment support from local test success.
Tests use temporary keys and isolated databases; no production credentials or settings are created.
