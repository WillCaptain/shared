# AIPP development skill — harness adapters

Portable skill: [`../aipp-development/`](../aipp-development/) (Agent Skills `SKILL.md` standard).

Adapters only know **where to install** that package for a given coding agent. They do **not** own protocol rules.

| Adapter | Installs core to |
|---------|------------------|
| [`aipp-skill-cursor/`](aipp-skill-cursor/) | `~/.cursor/skills/aipp-development` |
| [`aipp-skill-claude/`](aipp-skill-claude/) | `~/.claude/skills/aipp-development` |

```bash
# one harness
bash aipp-skill-cursor/install.sh
bash aipp-skill-claude/install.sh

# or both
bash ../install.sh all
```

Add a new harness by copying an adapter folder, changing the target directory in `install.sh`, and documenting host-specific notes in that adapter’s `SKILL.md`.
