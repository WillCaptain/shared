# User profile capability (`shared.user.profile/v1`)

User profile is a public Ones interface. Consumers depend on the stable operations
`user_profile_view`, `find_user`, and `get_principal`, never on a provider app id
or private URL. User One is the initial planned provider but is replaceable.

## Tools

| Tool | Purpose |
|------|---------|
| `user_profile_view` | Open a readonly public name-card pop for a canonical user id |
| `find_user` | Search users by query (name / username / email / exact UUID) |
| `get_principal` | Return the authenticated caller's identity and org context |

`user_profile_view` accepts `user_id` (canonical Ones UUID) and returns a
`pop_widget` with `widget_type: user-profile` and public fields:

```json
{
  "id": "uuid",
  "name": "Alice",
  "username": "alice",
  "whatsUp": "A short self-introduction",
  "phone": "+86…",
  "email": "a@example.com",
  "profilePhoto": "https://… or data:image/…"
}
```

Providers must not require consumers to know which AIPP owns the widget module.
The Host routes the named tool and mounts the registered widget.

## Browser interface

Type: `shared.user.profile/v1`

The provider module exports:

- `openProfile(hostApi, userId, fallbackName?)` — open the name-card pop
- `resolve(hostApi, userIds)` — resolve display names for canonical ids
- `find(hostApi, args)` — proxy `find_user`
- `principal(hostApi)` — proxy `get_principal`
- standard Host lifecycle hooks (`apply`, `unload`, `prepareFallback`, `applyFallback`, `isActive`)

A consumer must degrade explicitly when no provider is registered.
