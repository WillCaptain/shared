---
name: aipp-skill-claude
description: Claude Code adapter for the portable aipp-development skill. Use when installing or configuring AIPP protocol guidance for Claude Code (~/.claude/skills). Does not redefine AIPP protocol rules — always load the core skill.
---

# AIPP skill adapter — Claude Code

This adapter installs the **portable** core package into Claude Code’s skills directory.

| Role | Path |
|------|------|
| Core (source of truth) | `skills/aipp-development/` |
| Claude install target | `~/.claude/skills/aipp-development` → symlink to core |

## Install

```bash
./install.sh
# or from repo root:
# bash shared/aipp-protocol/skills/adapters/aipp-skill-claude/install.sh
```

## Claude-specific notes

- Skills under `~/.claude/skills/<name>/SKILL.md` auto-trigger from the core `description`.
- `~/.claude/skills/` is a **deployment destination**, not the canonical package location.
- After install, Claude loads the **core** `SKILL.md` via the symlink.

## Agent instruction

When this adapter is in context only to set up the harness: run `install.sh`, then follow **`../../aipp-development/SKILL.md`** for all AIPP build/deploy work. Never fork protocol rules into this file.
