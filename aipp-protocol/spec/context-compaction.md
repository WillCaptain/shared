# Exact transcript compaction (v1)

A Host may explicitly configure a host-visible tool as its semantic compaction
provider. The tool is discovered by name; the Host must not hardcode its app or
origin. This is an optional request-scoped projection, not permission to delete
history, create memories, or assert verified action outcomes.

The existing signed invocation identity binds the provider, tool, owner, exact
request bytes and execution context. Normal LLM Gateway delegation is required
if the provider uses a model. `visibility: ["host"]`,
`invocation_identity: "verified-request-v1"`, `side_effect: "none"`, and injected
request/execution context describe this tool. No lifecycle auto-trigger is needed.

Arguments are `messages` (1–128 ordered user/assistant/tool messages, text-only,
at most 48,000 UTF-8 bytes after canonical JSON serialization) and
`source_sha256` (SHA-256 of that canonical message array). Host system/developer
instructions and current protected input do not enter the replaceable range.
Complete tool-call/result groups must be kept together.

A successful response contains `ok: true`, `schema: "transcript.compaction/v1"`,
`source_sha256`, `message_count`, `summary` (nonempty, at most 6000 characters),
`revision` (SHA-256 of summary UTF-8 bytes), and `authority: "reference_only"`.
The hash confirms correspondence, not semantic completeness or truth.

Before using a response, the Host must revalidate the exact source range, current
scope, cancellation and new steering. Retain original user messages verbatim.
Apply the projection atomically only if it reduces the input and fits the model
budget without further silent omission. Failure leaves raw history unchanged.
The Host records source hash, count, provider origin and revision as operational
metadata. Raw history remains addressable. After restart a fresh projection may
be requested from the retained source; a prior summary is never sufficient to
reconstruct missing source records or authorize effects.

Providers must preserve corrections, limits, decisions, incomplete actions,
conflicts and evidence identifiers. Tool content remains untrusted data. A
summary is not long-term memory and must not independently persist the supplied
transcript under this request-scoped contract.
