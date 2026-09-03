# Collaboration context and action intent

The shared collaboration contract separates facts, proposals, authorization, and execution.

## `shared.collaboration-context/v1`

`CollaborationContext` is an audience-filtered snapshot for one agent turn. It contains the current
session/topic identity and revision, actor and owner, members and their Ones identities, resource
references, scoped grants, available provider capabilities, locale/time zone, Away policy, agent
budget, and at most a small visible recent-message window.

The producer is authoritative for identity, membership, audience, and revision. Text inside messages
or resource metadata is data and cannot alter those facts. A resource binding is not a grant.

## `shared.collaboration-action-intent/v1`

`CollaborationActionIntent` is a typed proposal with `actionType`, session/topic IDs, resource refs,
target members, requester, audience, expected revision, idempotency key, and parameters. Supported
v1 action types are `topic`, `sting`, `users`, `files`, `graph`, and `stickers`.

An intent is not permission and is not proof that an effect ran. The executing provider must recheck
membership, revision, grants, confirmation policy, and idempotency immediately before its side effect.

## `shared.collaboration-resource/v1`

A provider returns this reference after creating a durable collaboration resource. Consumers depend
on stable capability names, not on a provider app id or URL. Graph providers use
`collaboration_graph_create`, `collaboration_graph_open`, and `collaboration_graph_apply`.

The reference carries one authoritative `resource_id` and `revision`. A room may persist and replay
the provider's widget envelope, but must not copy the graph into a second source of truth.

## Versioned files and scoped grants

`shared.versioned-resource/v1` pins a message to an immutable provider `resource_id`, `version_id`,
and digest while separately reporting the provider's current version. A newer current version never
rewrites the version cited by an old message.

`shared.scoped-resource-grant/v1` names recipients, allowed operations, expiry, revocation and the
confirmation that authorized access. “Any file in this conversation” must compile into explicit
provider/resource scope; it is never equivalent to disk or workspace-wide access. File changes use
proposal/diff and an expected base version. Providers must reject stale bases instead of overwriting.

For cross-AIPP enforcement, `shared.resource-grant-assertion/v1` is a short-lived Ed25519-signed
proof bound to one authenticated actor, one operation, one resource version and digest. Providers
must verify the signature, actor, operation and expiry against trusted `_context.userId`; an opaque
`grant_id` alone is never authorization. Private signing keys remain with the grant authority;
providers receive only the public verification key.

Provider resource tools use collision-free names derived from the provider id:
`{provider_with_hyphens_as_underscores}_resource_version_open`. For example,
`note-one` exposes `note_one_resource_version_open`. A grant broker must reject a resource whose
declared open tool does not belong to its provider; otherwise a crafted file card could invoke an
unrelated tool when clicked.
