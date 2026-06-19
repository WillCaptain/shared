# AIPP — Coding Agent Guide

> **Version**: 2.8 · **Audience**: Codex, Claude Code, Cursor, and other **coding agents** building AIPP HTTP apps.

The canonical charter lives in the **`aipp-development` skill**:

→ **[`skills/aipp-development/SKILL.md`](skills/aipp-development/SKILL.md)** — read it first.

It contains: what an AIPP is (4 core endpoints), the gradual-discovery workflow, the non-negotiable rules table, and the task router into [`spec/`](spec/INDEX.md). One charter, one place — this file is intentionally just a pointer so the two can never drift.

- Harnesses with skill support (Claude Code): install/symlink `skills/aipp-development/` into your skills directory — it then auto-loads whenever a session touches AIPP work, no instruction needed.
- Harnesses without skill support: read the SKILL.md body directly (the frontmatter is just the trigger description).
- Bootstrap paste block (fallback, Tier 0): [`docs/tier0-bootstrap.prompt.md`](docs/tier0-bootstrap.prompt.md).

**Do not load the full [`README.md`](README.md) into context** — it is changelog + section stubs only; all normative text lives in [`spec/`](spec/INDEX.md). Use the charter + [`spec/INDEX.md`](spec/INDEX.md).
