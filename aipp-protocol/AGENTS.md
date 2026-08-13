# AIPP — Coding Agent Guide

> **Version**: 2.10 · **Audience**: Codex, Claude Code, Cursor, and other **coding agents** building AIPP HTTP apps.

The canonical charter lives in the portable **`aipp-development`** skill (Agent Skills standard):

→ **[`skills/aipp-development/SKILL.md`](skills/aipp-development/SKILL.md)** — read it first.

It contains: what an AIPP is (4 core endpoints), the gradual-discovery workflow, the non-negotiable rules table, and the task router into [`spec/`](spec/INDEX.md). One charter, one place — this file is intentionally just a pointer so the two can never drift.

**Install into a harness** (home dirs are deployment targets, not source of truth):

```bash
# Cursor → ~/.cursor/skills/aipp-development
bash skills/adapters/aipp-skill-cursor/install.sh

# Claude Code → ~/.claude/skills/aipp-development
bash skills/adapters/aipp-skill-claude/install.sh

# both
bash skills/install.sh all
```

See [`skills/README.md`](skills/README.md) and [`skills/adapters/README.md`](skills/adapters/README.md).

- Harnesses without skill support: read the core `SKILL.md` body directly (frontmatter is the trigger description).
- Bootstrap paste block (fallback, Tier 0): [`docs/tier0-bootstrap.prompt.md`](docs/tier0-bootstrap.prompt.md).

**Do not load the full [`README.md`](README.md) into context** — it is changelog + section stubs only; all normative text lives in [`spec/`](spec/INDEX.md). Use the charter + [`spec/INDEX.md`](spec/INDEX.md).
