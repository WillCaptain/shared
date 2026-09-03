# Contacts capability (`shared.contacts/v1`)

Contacts is a public Ones interface. Consumers depend on the stable operations
`contacts_search` and `contacts_resolve`, never on a provider app id or private URL.
Chat One is the initial planned provider but is replaceable.

`contacts_search` accepts `query`, optional `tags`, `cursor`, and `limit`.
`contacts_resolve` accepts `user_ids`. Both return contacts visible to the authenticated
caller. Providers must not expose an unrestricted organization directory.

```json
{"user_id":"uuid","display_name":"Alice","avatar_url":"...","tags":["friend","coworker"]}
```

`tags` is an extensible array; `friend` and `coworker` are the standard v1 values.
The optional browser interface uses type `shared.contacts/v1` and exports equivalent
search/resolve functions. A consumer must degrade explicitly when no provider exists.
