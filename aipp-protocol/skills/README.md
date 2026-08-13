# Coding-agent skills for AIPP protocol

```
skills/
  aipp-development/     # portable Agent Skills package (charter + references)
  adapters/             # harness installers (Cursor, Claude Code, …)
  install.sh            # ./install.sh [cursor|claude|all]
```

1. **Edit protocol guidance** only under `aipp-development/`.
2. **Install into a harness** via the matching adapter (symlink; home dirs are not source of truth).
3. **Normative protocol text** remains in `../spec/` — the skill is a charter + router.

See [`adapters/README.md`](adapters/README.md).
