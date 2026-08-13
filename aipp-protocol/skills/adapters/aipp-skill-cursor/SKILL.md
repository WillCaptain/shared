---
name: aipp-skill-cursor
description: Cursor adapter for the portable aipp-development skill. Use when installing or configuring AIPP protocol guidance for Cursor Agent Skills (~/.cursor/skills). Does not redefine AIPP protocol rules — always load the core skill.
---

# AIPP skill adapter — Cursor

This adapter installs the **portable** core package into Cursor’s personal skills directory.

| Role | Path |
|------|------|
| Core (source of truth) | `skills/aipp-development/` |
| Cursor install target | `~/.cursor/skills/aipp-development` → symlink to core |

## Install

```bash
./install.sh
# or from repo root:
# bash shared/aipp-protocol/skills/adapters/aipp-skill-cursor/install.sh
```

## Cursor-specific notes

- Personal skills live under `~/.cursor/skills/` (not `~/.cursor/skills-cursor/`, which is reserved for built-ins).
- Project-scoped alternative: symlink core into `<repo>/.cursor/skills/aipp-development` if the team wants repo-local discovery.
- Multi-root workspaces: protocol paths in the core charter assume a root that contains `shared/aipp-protocol/`.
- After install, Cursor discovers `name` + `description` from the **core** `SKILL.md` (symlink target).

## Agent instruction

When this adapter is in context only to set up the harness: run `install.sh`, then follow **`../../aipp-development/SKILL.md`** for all AIPP build/deploy work. Never fork protocol rules into this file.
