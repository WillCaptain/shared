# Tier 0 — Paste into Agent Config (fallback)

> **Version:** 2.10  
> **Preferred Tier 0:** install the portable [`aipp-development` skill](../skills/aipp-development/SKILL.md) via a harness adapter:
> - Cursor: `bash skills/adapters/aipp-skill-cursor/install.sh` → `~/.cursor/skills/aipp-development`
> - Claude Code: `bash skills/adapters/aipp-skill-claude/install.sh` → `~/.claude/skills/aipp-development`
> - Both: `bash skills/install.sh all`  
> It then auto-triggers on AIPP work with no pasted config.  
> This paste block is the **fallback** for harnesses without skill support.  
> **Keep this file in git** — it is the canonical snippet; update it when the protocol changes.

---

## Copy from here

```text
AIPP development (protocol v2.10)

You build standalone HTTP "AIPP" apps consumed by a Host (world-one).

DISCOVERY (do not load full README):
1. Read shared/aipp-protocol/skills/aipp-development/SKILL.md (portable charter; AGENTS.md points there).
2. Classify task → aipp-protocol/spec/INDEX.md → load ONLY one spec (field-semantics, widgets, tool-responses, skills, …).
3. If editing visibility/owner_widget/mutates_display/refresh_tool → read spec/field-semantics.md FIRST.
4. Shared capabilities / CSS / deploy indexes: skills/aipp-development/references/*.md
5. Java assert* wins over prose (spec/verify.md).

HARD RULES:
- Implement GET /api/app, GET /api/tools, GET /api/widgets, POST /api/tools/{name}.
- Exactly one widget per app with is_main:true; app_id kebab-case, consistent across endpoints.
- Never register sys.* in GET /api/widgets; you MAY return sys.* in tool responses (see spec/system-widgets.md).
- Tools = single LLM call; Skills = multi-step SKILL.md playbooks via GET /api/skills.
- Tool entries must NOT include prompt, tools[], or resources.
- Tricky fields: read spec/field-semantics.md — placement (visibility/owner_widget/router_promoted) ≠ mutates_display ≠ refresh_tool.
- Tool placement (v3): flat fields on /api/tools — NOT nested scope.
- Editable widgets: refresh_tool on widget; mutates_display on write tools — NOT mutating_tools on widget.
- Widgets: shared .aipp-* / --aipp-* only — no local CSS (references/ui-primitives.md).
- Cross-app tools: depend on tool names via requires/allowed-tools (references/capability-catalog.md).
- Capability tree: routable leaves are kind tool/skill (ref.name = registered name); kind widget is catalog-only, not a Router target.

DONE GATE:
- Before finishing, validate JSON with aipp-protocol assert* (spec/verify.md) or run the app's compliance tests.
- Deploy/attach: references/deploy.md + host-lifecycle / host-registration.
```

---

## Path variants

| Layout | AGENTS.md path |
|--------|----------------|
| Monorepo (`shared/aipp-protocol`) | `shared/aipp-protocol/AGENTS.md` |
| Git submodule / sibling clone | `aipp-protocol/AGENTS.md` |
| Maven dependency only | Clone or vendor `AGENTS.md` + `spec/` next to the app |

Adjust the path in the pasted block to match the workspace.
