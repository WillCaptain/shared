# Compatibility rules

- Semantic versioning applies after the first registry release.
- Adding optional fields or SSE event types is backward compatible.
- Removing or renaming fields, changing field meaning, or tightening an existing accepted range is
  breaking.
- Identity and billing fields are permanently forbidden in request bodies.
- A client must ignore unknown response fields and unknown SSE event types.
- Published release versions are immutable and must never be overwritten.
- `model_alias` is a stable logical identifier. Mapping it to a different provider model or price
  requires an explicit registry/pricing rollout; raw provider model names are never implicit aliases.
