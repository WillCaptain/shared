# Host AIPP catalog

`GET /api/host/aipps` is the provider-neutral, read-only aggregate of AIPPs currently
known to an AIPP Host. The executable constants and client are
`HostAippCatalogSpec` and `HostAippCatalogClient`.

The Host implements this aggregate because it owns registration and liveness. Consumers
such as Ones Helper depend only on this shared contract; they must not import Host
registry classes or use a Host-specific private API.

The response contains `schema_version`, public user-facing `apps`, and AIPP-owned
`help_contributions`. The Host validates and stamps each contribution with its declaring
`app_id`, but does not rewrite its meaning. It never exposes base URLs, credentials,
internal registry records, system prompts, or tool implementations.

A help action with `kind: tool` may carry `arguments`. The target AIPP resolves identities
and resource locations and enforces authorization. For example, Chat One—not a helper—
resolves `person_query: Alice` into the user's conversation and opens that exact position.
