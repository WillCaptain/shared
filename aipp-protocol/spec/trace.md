# Operational Trace — cross-surface UX / integration timeline

> **Purpose:** Record what the user clicked, which tools ran, and how UI/session/widget state changed — in one correlated timeline for bug diagnosis.  
> **Not a replacement for:** note-one `change_log` (domain KB audit / revert) or world-event business semantics.  
> **Normative companion:** [`db-operations.md`](db-operations.md) for host persistence; [`events.md`](events.md) for world-event protocol.

---

## 1. Problem

Integration bugs in the Once stack (duplicate `sys.confirms` in collapsed canvas chat, `callTool` vs `proxyTool`, task status idle/pending) are hard to describe in prose. Trace gives a pasteable JSONL export: *what I clicked → which tool ran → what changed → whether dedup fired*.

---

## 2. Architecture

```
┌─────────────┐  POST /api/trace/events   ┌──────────────────────────────┐
│ ones-shell  │ ─────────────────────────►│ world-one (collector + sink) │
│ (Electron)  │                           │  trace_events table          │
└─────────────┘                           │  GET /api/trace (export)     │
┌─────────────┐  POST /api/trace/events   └──────────────────────────────┘
│ index.html  │ ─────────────────────────►         ▲
│ (host UI)   │                                    │ read
└─────────────┘                                    │
┌─────────────┐  POST /api/trace/events            │
│ note-one    │ ───────────────────────────────────┘
│ (AIPP)      │
└─────────────┘

shared/trace-client     — Java types + sanitizer (AIPPs, host Java)
static/trace-client.js  — browser + ones-shell (copy or symlink)
```

**Central sink:** world-one host DB. All surfaces emit to the same stream; correlation ties cross-process spans together.

**Distribution (normative):** shared jar + host API — **not** a trace ingest AIPP.

| Consumer | Emit path |
|----------|-----------|
| world-one Java | `TraceService.ingest()` in-process |
| AIPPs (note-one, …) | `trace-client` → `TraceHttpEmitter` → `POST /api/trace/events` |
| host UI / ones-shell | `trace-client.js` → same API |

One Maven module (`shared/trace-client`); no copy-paste per app. JS loads from host static or bundles the same file.

**Phase 3 (optional):** read-only trace viewer AIPP or `sys.trace` widget (`GET /api/trace/export` only).

---

## 3. Event schema v0

Each row is one observable fact. JSONL export = one JSON object per line.

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `schema_version` | int | yes | `0` |
| `trace_id` | string (uuid) | yes | Unique event id |
| `parent_id` | string \| null | no | Direct causal parent (`trace_id` of triggering event) |
| `correlation_id` | string (uuid) | yes | Shared across one user gesture / workflow branch |
| `user_id` | string | yes | Host user id |
| `ts` | ISO-8601 instant | yes | Event time (UTC) |
| `actor` | enum | yes | `user` \| `system` \| `agent` |
| `surface` | enum | yes | See §4 |
| `action` | string | yes | Dot-separated verb, see §5 |
| `target` | object | no | Scoped ids only — see §6 |
| `request` | object | no | Trimmed input (size cap) |
| `response` | object | no | Trimmed output (size cap) |
| `outcome` | enum | yes | `ok` \| `error` \| `deduped` \| `skipped` |

### Correlation model

1. **User gesture** (click task row, pick folder, Continue on confirm) → client calls `beginGesture()` → new `correlation_id`.
2. **Reactions** (tool proxy, widget append, world event create) → same `correlation_id`, `parent_id` = gesture or prior reaction `trace_id`.
3. **Server-only paths** (background sync) → system generates `correlation_id`; link via `target.job_id` / `target.batch_id`.
4. **Phase 2:** propagate `X-Trace-Correlation-Id` + `X-Trace-Parent-Id` on tool proxy and AIPP HTTP.

---

## 4. Surfaces

| Value | Emitter |
|-------|---------|
| `host` | world-one Java (proxy, world events, session store) |
| `host_ui` | index.html |
| `task_panel` | Task panel clicks / badge |
| `canvas_chat` | Collapsed/expanded chat rail, widget messages |
| `ones-shell` | Electron main/preload IPC |
| `note-one` | note-one AIPP tools/services |
| `agent` | Generic agent loop (future) |

---

## 5. MVP action catalog

### Workspace

| Action | Actor | Typical target |
|--------|-------|----------------|
| `workspace.set` | user/system | `workspace_scope` (basename hash, not full path) |
| `workspace.switch` | user | `workspace_scope`, `previous_scope` |
| `onboarding.open` | user/system | `widget_type` |
| `onboarding.apply` | user | `workspace_scope` |

### Import (ones-shell → note-one)

| Action | Actor | Typical target |
|--------|-------|----------------|
| `import.folder_pick` | user | `batch_id` |
| `import.start` | system | `batch_id`, `file_count` |
| `import.chunk` | system | `batch_id`, `chunk_index`, `tool_name` |
| `import.complete` | system | `batch_id`, `change_log_seq` (ref only) |
| `import.cancel` | user | `batch_id` |
| `import.rollback` | user/system | `batch_id` |

### Sync / steward

| Action | Actor | Typical target |
|--------|-------|----------------|
| `sync.plan` | system | `batch_id` |
| `sync.apply` | system | `batch_id` |
| `triage.propose` | system | `job_id`, `cluster_count` |
| `triage.open` | user/system | `job_id`, `event_id` |
| `triage.apply` | user | `job_id`, `confirm_id`, `event_id` |
| `layout.apply` | system | `job_id`, `op_count` |

### UI / host

| Action | Actor | Typical target |
|--------|-------|----------------|
| `ui.click` | user | `element`, `event_id`, `session_id` |
| `ui.badge_click` | user | `event_id` |
| `canvas.collapse` / `canvas.expand` | user | `session_id` |
| `session.switch` | user/system | `session_id`, `previous_session_id` |
| `widget.append` | system | `session_id`, `widget_type`, `instance_key` |
| `widget.dedup` | system | `session_id`, `widget_type`, `instance_key` |
| `tool.proxy` | system | `tool_name`, `app_id`, `session_id` |
| `tool.result` | system | `tool_name`, `status_code` |
| `world_event.create` | system | `event_id`, `event_type`, `status` |
| `world_event.status` | system | `event_id`, `status` |

---

## 6. Target object (privacy-safe)

Allowed keys (extend additively):

`session_id`, `event_id`, `job_id`, `confirm_id`, `tool_name`, `batch_id`, `app_id`, `widget_type`, `instance_key`, `workspace_scope`, `element`, `status`, `status_code`, `cluster_count`, `file_count`, `chunk_index`, `op_count`, `change_log_seq`, `machine_id`

**Never store:** full filesystem paths, file bytes, API keys, tokens, raw document content.

`workspace_scope` = last path segment or SHA-256 prefix of normalized path (client chooses; must be stable for one workspace).

---

## 7. Privacy, retention, export

### Sanitization (shared `PayloadSanitizer`)

- Strip keys matching `(?i)(api[_-]?key|token|password|authorization|secret|cookie)`.
- Truncate string values > 512 chars; truncate serialized JSON blobs > 4 KiB with `"_truncated": true`.
- Omit `html_widget` body beyond `widget_type` + `title` + stable ids.

### Retention (ring buffer per `user_id`)

- Keep newest **10 000** rows **or** **7 days**, whichever evicts first.
- Eviction runs on insert batch (host `TraceService`).

### Export API

```
GET /api/trace?since=<ISO>&until=<ISO>&correlation_id=<uuid>&limit=500
GET /api/trace/export?since=<ISO>&minutes=5   → application/x-ndjson
POST /api/trace/events   body: { "events": [ ...TraceEvent... ] }
```

Local dev: no auth gate in Phase 1; production should scope by session user (future).

---

## 8. Module layout

```
shared/
  trace-client/                          # Maven jar
    pom.xml
    src/main/java/org/twelve/shared/trace/
      TraceEvent.java
      TraceActions.java                  # string constants
      TraceSurfaces.java
      TraceOutcomes.java
      TraceIds.java                      # uuid helpers
      PayloadSanitizer.java

  aipp-protocol/spec/trace.md            # this file

ones/world-one/
  src/main/java/.../trace/
    TraceController.java
    TraceService.java
    db/JdbcTraceDb.java
    db/TraceEventEntity.java
  src/main/resources/static/trace-client.js
  WorldOneDbSchemaInit — trace_events table
  ToolProxyController — tool.proxy / tool.result
  index.html — host_ui hooks

ones/ones-shell/
  src/trace-client.js                    # copy or require from host static
  workspace-bootstrap.js, folder-import.js — emit import.* / workspace.*

ones/note-one/
  TriageService, ingest paths — emit triage.* / import.* (Phase 1b)
```

---

## 9. Example timeline — workspace → import → steward → Continue

Scenario: user sets workspace, imports a folder, sync proposes steward clusters, clicks task panel, clicks Continue.

```jsonl
{"schema_version":0,"trace_id":"a1","parent_id":null,"correlation_id":"c-gesture-1","user_id":"001","ts":"2026-07-09T07:00:01.000Z","actor":"user","surface":"ones-shell","action":"workspace.set","target":{"workspace_scope":"once"},"outcome":"ok"}
{"schema_version":0,"trace_id":"a2","parent_id":"a1","correlation_id":"c-gesture-1","user_id":"001","ts":"2026-07-09T07:00:01.050Z","actor":"system","surface":"host","action":"tool.proxy","target":{"tool_name":"set_workspace","session_id":"main"},"request":{"args_keys":["path","machine_id"]},"outcome":"ok"}
{"schema_version":0,"trace_id":"a3","parent_id":"a2","correlation_id":"c-gesture-1","user_id":"001","ts":"2026-07-09T07:00:01.200Z","actor":"system","surface":"host","action":"tool.result","target":{"tool_name":"set_workspace","status_code":200},"outcome":"ok"}
{"schema_version":0,"trace_id":"b1","parent_id":null,"correlation_id":"c-import-1","user_id":"001","ts":"2026-07-09T07:01:10.000Z","actor":"user","surface":"ones-shell","action":"import.folder_pick","target":{"batch_id":"imp-7f3a","file_count":42},"outcome":"ok"}
{"schema_version":0,"trace_id":"b2","parent_id":"b1","correlation_id":"c-import-1","user_id":"001","ts":"2026-07-09T07:01:10.100Z","actor":"system","surface":"host","action":"tool.proxy","target":{"tool_name":"wiki_ingest_folder","session_id":"app-note-one"},"outcome":"ok"}
{"schema_version":0,"trace_id":"b3","parent_id":"b2","correlation_id":"c-import-1","user_id":"001","ts":"2026-07-09T07:01:45.000Z","actor":"system","surface":"note-one","action":"import.complete","target":{"batch_id":"imp-7f3a","change_log_seq":128},"outcome":"ok"}
{"schema_version":0,"trace_id":"c1","parent_id":null,"correlation_id":"c-sync-1","user_id":"001","ts":"2026-07-09T07:02:00.000Z","actor":"system","surface":"ones-shell","action":"sync.plan","target":{"batch_id":"sync-auto"},"outcome":"ok"}
{"schema_version":0,"trace_id":"c2","parent_id":"c1","correlation_id":"c-sync-1","user_id":"001","ts":"2026-07-09T07:02:01.000Z","actor":"system","surface":"host","action":"tool.proxy","target":{"tool_name":"wiki_triage_propose"},"outcome":"ok"}
{"schema_version":0,"trace_id":"c3","parent_id":"c2","correlation_id":"c-sync-1","user_id":"001","ts":"2026-07-09T07:02:01.500Z","actor":"system","surface":"note-one","action":"triage.propose","target":{"job_id":"job-9ac2","cluster_count":3},"outcome":"ok"}
{"schema_version":0,"trace_id":"c4","parent_id":"c3","correlation_id":"c-sync-1","user_id":"001","ts":"2026-07-09T07:02:01.600Z","actor":"system","surface":"host","action":"world_event.create","target":{"event_id":"evt-stew-1","event_type":"workspace_steward","status":"pending"},"outcome":"ok"}
{"schema_version":0,"trace_id":"d1","parent_id":null,"correlation_id":"c-click-task","user_id":"001","ts":"2026-07-09T07:03:00.000Z","actor":"user","surface":"task_panel","action":"ui.click","target":{"event_id":"evt-stew-1","element":"pending_event_row"},"outcome":"ok"}
{"schema_version":0,"trace_id":"d2","parent_id":"d1","correlation_id":"c-click-task","user_id":"001","ts":"2026-07-09T07:03:00.050Z","actor":"system","surface":"host_ui","action":"session.switch","target":{"session_id":"app-note-one","previous_session_id":"main"},"outcome":"ok"}
{"schema_version":0,"trace_id":"d3","parent_id":"d1","correlation_id":"c-click-task","user_id":"001","ts":"2026-07-09T07:03:00.100Z","actor":"system","surface":"host","action":"tool.proxy","target":{"tool_name":"wiki_triage_open","job_id":"job-9ac2"},"outcome":"ok"}
{"schema_version":0,"trace_id":"d4","parent_id":"d3","correlation_id":"c-click-task","user_id":"001","ts":"2026-07-09T07:03:00.400Z","actor":"system","surface":"host_ui","action":"widget.append","target":{"session_id":"app-note-one","widget_type":"sys.confirms","instance_key":"steward:job-9ac2"},"outcome":"ok"}
{"schema_version":0,"trace_id":"e1","parent_id":null,"correlation_id":"c-continue","user_id":"001","ts":"2026-07-09T07:03:30.000Z","actor":"user","surface":"canvas_chat","action":"ui.click","target":{"element":"confirm_continue","confirm_id":"cf-1","job_id":"job-9ac2"},"outcome":"ok"}
{"schema_version":0,"trace_id":"e2","parent_id":"e1","correlation_id":"c-continue","user_id":"001","ts":"2026-07-09T07:03:30.100Z","actor":"system","surface":"host","action":"tool.proxy","target":{"tool_name":"wiki_triage_apply"},"outcome":"ok"}
{"schema_version":0,"trace_id":"e3","parent_id":"e2","correlation_id":"c-continue","user_id":"001","ts":"2026-07-09T07:03:30.500Z","actor":"system","surface":"ones-shell","action":"layout.apply","target":{"job_id":"job-9ac2","op_count":5},"outcome":"ok"}
{"schema_version":0,"trace_id":"e4","parent_id":"e2","correlation_id":"c-continue","user_id":"001","ts":"2026-07-09T07:03:31.000Z","actor":"system","surface":"host","action":"world_event.status","target":{"event_id":"evt-stew-1","status":"resolved"},"outcome":"ok"}
```

**Dedup bug example:** if user re-clicks the same steward task, line `d4` would instead be:

```json
{"action":"widget.dedup","outcome":"deduped","target":{"instance_key":"steward:job-9ac2","session_id":"app-note-one"},"parent_id":"d1","correlation_id":"c-click-task-2"}
```

---

## 10. Phasing

| Phase | Scope |
|-------|-------|
| **0** | This spec + action catalog + example timeline |
| **1** | `trace-client` + host ingest/export + hooks: `ToolProxyController`, `routeHostTool`, `displayChatWidgetMessage`, `handlePendingEventClick`, `switchSession` |
| **1b** | ones-shell + note-one critical paths |
| **2** | Header propagation, dedup markers everywhere, `parent_id` chains |
| **3** | `sys.trace` viewer widget |

---

## 11. Why not OpenTelemetry

OpenTelemetry excels at distributed service metrics/traces with vendor backends. This stack needs a **pasteable, human-readable JSONL bug report** tied to Once-specific surfaces (`sys.confirms` dedup, task panel, steward `job_id`) with **no file content** and **minimal ops overhead**. A thin custom schema keeps export self-describing for LLM diagnosis. OTel can wrap later if metrics backends are adopted.

---

## 12. Relationship to other logs

| System | Role |
|--------|------|
| **trace** | Operational UX/integration timeline; ephemeral ring buffer |
| **note-one change_log** | KB mutation audit; revertable |
| **world_events** | Human-in-the-loop workflow state; task panel source |
| **db-ops SQL logs** | Low-level query audit |

Trace may **reference** `change_log_seq` or `event_id` but must not duplicate change payloads.
