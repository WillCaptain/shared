# AIPP extension packs — Host-published overlays for an installed AIPP

**Audience:** Host developers, Once / ones-shell developers, pack authors.

**Depends on:** [`app-manifest.md`](app-manifest.md) (`app_description`), [`skills.md`](skills.md), [`client-execution.md`](client-execution.md) §8, [`client-bootstrap.md`](client-bootstrap.md).

**Discovery:** [`INDEX.md`](INDEX.md) → this file.

---

## 1. Problem

The Host agent loop routes turns by scanning **installed AIPP summaries** (forest roots + promoted leaves). A base AIPP such as `computer-use-one` stays **domain-generic** (“desktop executor”). Local apps (Safari bookmarks, Calendar, …) are **not** Host or base-AIPP knowledge.

Operators publish **extension packs** that attach to a `target_app`. Without a dedicated announcement field, the Host still only sees the base `app_description` and may fail to bind the correct AIPP after a pack is installed on Once.

---

## 2. Three pack surfaces

An extension pack **MAY** ship any combination of:

| Surface | Extends | Owner at runtime |
|---------|---------|------------------|
| **`skills/`** | Target AIPP playbooks (`GET /api/skills` overlay) | Host `SkillCatalog` |
| **`client/`** | Once local capability (`client_package` + tools / `host-handler.js`) | Desktop shell |
| **`capability_extension`** | Effective **AIPP summary** used for Tier‑1 routing / root recall | Host merge into `target_app` |

```text
{pack-id}/
  package.json              # id, version, title, target_app, description, capability_extension
  skills/{skill_id}/SKILL.md
  client/
    client-package.json
    tools.json
    app.jar | host-handler.js
```

| Field | Audience | Normative role |
|-------|----------|----------------|
| `description` | Operator / Admin UI | Human blurb only — **not** router input |
| `capability_extension` | Host router / AIPP root summary | Short text Host **appends** to the effective description of `target_app` |
| `target_app` | Host | AIPP `app_id` that receives skills + description append |

**MUST NOT** put product-domain nouns for optional local apps into the base AIPP jar’s `app_description` or into Host core ambient prompts. Those nouns belong in pack `capability_extension` (and skill / tool copy inside the pack).

---

## 3. Effective AIPP description

Host **MUST** compute an **effective description** for routing and root summaries:

```
effective = base_app_description
          + join(" ", capability_extension of each active pack for this target_app)
```

- **Base** comes from the AIPP `GET /api/app` `app_description` (or Host’s existing root-description derivation).
- **Appends** are pack `capability_extension` strings, trimmed, non-empty, de-duplicated in stable pack-id order.
- Host **MUST NOT** mutate the AIPP’s on-wire `GET /api/app` response on the AIPP process; the merge is a **Host-side** view for router / forest / catalog consumers that use Host-cached manifests.

Promoted skill leaves remain a separate surface (`router_promoted`). `capability_extension` answers “why bind this AIPP”; the skill answers “which playbook after bind.”

---

## 4. Install gate (normative)

Packs that ship a **client capability** must not overclaim.

| Pack shape | When `capability_extension` is active |
|------------|----------------------------------------|
| Skills only (no `client/` capability) | After Host **publish** |
| Has `client_package` / `client.capability` | Only when this chat session’s Once executor **advertises** that capability (installed + registered) |

- If no client executor is connected for the session, Host **MUST NOT** append client-gated extensions.
- Skills may still be indexed after publish (playbook available once tools exist); the **description append** follows the install gate above so Tier‑1 AIPP scan stays honest.
- Catalog / Tools UI may still list unpublished-install rows; that UI uses pack `description`, not `capability_extension`.

---

## 5. Authoring guidance

`capability_extension` **SHOULD** be one or two short sentences, English first (LocalizedString optional in a later revision), stating **what local ability** the target AIPP now has — not operator install instructions.

Example:

```json
{
  "id": "safari-bookmarks",
  "version": "0.1.5",
  "title": "Safari Bookmarks",
  "target_app": "computer-use-one",
  "description": "Safari bookmark library for Once (Admin UI).",
  "capability_extension": "Can manage Safari bookmark folders and favorites on the local Mac via Once (bookmark library only, not page browsing)."
}
```

---

## 6. Host responsibilities

1. Persist `capability_extension` on publish (manifest row).
2. Expose merge helper used by forest root summaries and any Host AIPP catalog that surfaces descriptions to the router.
3. Pass the session’s installed client capabilities into the merge (install gate).
4. Keep Host **core** free of pack-domain product copy (no hardcoded Safari-bookmarks capability text in world-one routing prompts).

---

## 7. Out of scope

- Auto-install of client packages without Once Plugin confirm (see product policy on Tools UX).
- Mutating remote AIPP JVM jars.
- Replacing `prompt_contributions` — packs **MAY** later also ship ambient contributions; v1 uses description append only.
