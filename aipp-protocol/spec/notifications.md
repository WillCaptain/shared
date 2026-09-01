# AIPP Host Notification Protocol

> **Status:** normative, v1. The shared Java source of truth is
> `org.twelve.aipp.host.HostNotificationSpec`.

## Ownership

The Host owns notification persistence and inbox lifecycle. An AIPP owns the decision to
publish, update, act on, dismiss, or resolve a notification. The Host treats `kind`,
`group_key`, `title_key`, and `payload` as opaque application data.

All operations use the authenticated Host-to-AIPP identity headers. The Host derives the user
and app identity from authentication and never accepts either identity from the JSON body.

## Operations

All endpoints accept `POST` JSON under `/api/host/integrations/notifications`:

| Path | Required identity | Purpose |
|---|---|---|
| `/publish` | `group_key` | Insert or update an occurrence and return `notification_id` |
| `/update` | `notification_id` | Update content without changing inbox state |
| `/act` | `notification_id` | Record that the target action was performed |
| `/dismiss` | `notification_id` | Persist a user dismissal |
| `/resolve` | `notification_id` | Mark the notification no longer applicable |
| `/dismiss-occurrence` | `notification_id`, or occurrence `group_key` + opaque payload | Dismiss an existing notification or create a pre-publication tombstone |

`/publish` also requires `kind`, `level`, and `title_key`; optional content is `body` and an
object `payload`. A successful publish returns the stable `notification_id`. Subsequent target
actions MUST use that ID. `group_key` is only a collapse/idempotency identity and recovery key;
it is not the normal consumption identity.

The Host MUST NOT parse application resource IDs, phases, kinds, or state from these fields.
An AIPP retrying the same occurrence must receive the same notification identity and must not
create duplicate inbox rows.

## Failure behavior

Missing required fields return HTTP 400. Unknown notification IDs are idempotent no-ops and
return a boolean result. Transport and non-2xx failures remain visible to the AIPP; a scheduled
handler whose required notification publish fails returns `retryable_failed` rather than
acknowledging completion.
