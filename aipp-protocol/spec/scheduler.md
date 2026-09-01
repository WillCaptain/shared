# AIPP Scheduler Protocol

> **Status:** normative, v1. The shared Java source of truth is
> `org.twelve.aipp.scheduler`.

> **Coding-agent evidence path:** use this document for behavior, the shared
> package above for interfaces/wire models, and `AippScheduleSpecTest` for the
> executable compatibility gate. Do not infer the contract from a Host or AIPP
> implementation; those are adapters and may contain migration-era behavior.

## 1. Purpose and ownership

The Host provides durable one-shot scheduling to every registered AIPP. The
Host owns the clock, persistence, leasing, retry policy, and delivery. An AIPP
owns named business handlers and schedules jobs through the Host API.

A job is uniquely addressed by `(app_id, user_id, job_key)`. `app_id` and
`user_id` MUST come from authenticated request context. A Host MUST NOT trust
ownership fields supplied in a JSON body.

Version 1 is intentionally one-shot. A recurring workflow schedules its next
occurrence after successfully handling the current occurrence. This avoids
ambiguous cron time zones and catch-up behavior in the base contract.

The Host provides three scheduling levels:

| Level | Dispatch cadence | Intended use |
|---|---:|---|
| `coarse` | 15 seconds | Maintenance and non-urgent background work |
| `normal` | 8 seconds | Reminders and ordinary scheduled work |
| `precise` | 1 second | Short timers and time-sensitive notifications |

The cadence is the maximum interval between dispatch-round starts under normal
load, not an exact execution-time guarantee. For backward compatibility,
`coarse` (15 seconds) is the default when no level is supplied.

## 2. AIPP handler discovery and registration

An AIPP that supports scheduled callbacks exposes:

```http
GET /api/schedules
```

```json
{
  "app": { "app_id": "calendar-one" },
  "handlers": [
    { "name": "reminder.due" }
  ]
}
```

Handler names MUST match `[a-z][a-z0-9_.-]{0,79}` and be unique in the app.
The callback path is fixed by the protocol as
`POST /api/schedules/{handler}`. Apps MUST NOT advertise arbitrary callback
URLs. The Host reads this endpoint during normal registration/refresh and MUST
reject duplicate or invalid names.

The shared `ScheduleHandlerRegistration`, `AippScheduleHandler`, and
`AippScheduleRegistrar` types are the portable registration and callback
interfaces. An AIPP registers each handler with `AippScheduleRegistrar`; the
registration handle is closeable so app lifecycle code can deregister it.
Registering the same handler name twice MUST fail. Framework adapters expose
the registrar snapshot through `GET /api/schedules` and route
`POST /api/schedules/{handler}` to the matching handler. Framework-specific
bean discovery belongs outside the protocol module.

## 3. AIPP-to-Host job API

An authenticated AIPP creates or replaces a job with:

```http
PUT /api/host/schedules/{job_key}
Content-Type: application/json
```

```json
{
  "handler": "reminder.due",
  "fire_at": 1787990400000,
  "level": "coarse",
  "payload": { "reminder_id": "rem-42" }
}
```

`fire_at` is a positive Unix epoch millisecond. `payload` is a JSON object.
`level` is `coarse`, `normal`, or `precise`; when omitted it is `coarse`
(15 seconds), preserving the behavior of jobs created before levels existed.
`job_key` contains 1–200 characters after URL decoding and trimming. Upsert is
idempotent in the authenticated ownership namespace and returns the resulting
job representation.

Additional operations:

```http
DELETE /api/host/schedules/{job_key}
GET    /api/host/schedules/{job_key}
GET    /api/host/schedules?job_key_prefix=reminder:
```

Deleting an absent or already terminal job is idempotent. Listing MUST only
return jobs owned by the authenticated app and user. The shared
`HostScheduler` port defines the equivalent in-process boundary.

## 4. Host-to-AIPP delivery

When due, the Host atomically leases a job and sends:

```http
POST /api/schedules/reminder.due
X-AIPP-App-Id: calendar-one
X-AIPP-User-Id: user-7
X-AIPP-Schedule-Delivery-Id: job-123:2
Content-Type: application/json
```

```json
{
  "job_id": "job-123",
  "job_key": "reminder:rem-42",
  "handler": "reminder.due",
  "fire_at": 1787990400000,
  "attempt": 2,
  "payload": { "reminder_id": "rem-42" }
}
```

The callback uses the same authenticated Host-to-AIPP channel as other Host
callbacks. Implementations MUST validate that the path handler equals the body
handler. `X-AIPP-Schedule-Delivery-Id` is stable for one attempt.

The AIPP acknowledges with one of:

```json
{ "status": "completed" }
{ "status": "retryable_failed", "retry_at": 1787990460000, "error": "dependency unavailable" }
{ "status": "terminal_failed", "error": "invalid business payload" }
{ "status": "cancelled" }
```

`retryable_failed` means the business handler did not complete its required side effects and
the Host must retry at the supplied future `retry_at`. `terminal_failed` means retry cannot
make the delivery valid and the Host must dead-letter it immediately.

The corresponding shared types are `ScheduleFireRequest` and
`ScheduleFireResult`. A non-2xx response, timeout, malformed response, or lost
lease is retryable under Host policy. After the configured attempt limit, the
Host moves the job to `dead_letter` and exposes the failure operationally.

## 5. Delivery guarantees

Each level has a **single-flight dispatch round**. Before starting a cadence
tick, the level worker atomically acquires its round guard. If the preceding
round for that same level is still running, the Host MUST skip the new tick;
it MUST NOT queue or overlap another round. A skipped tick does not alter job
state: pending jobs remain eligible for the next round. The three levels have
independent guards, so a slow coarse round does not block precise dispatch.

In a multi-Host deployment the guard MUST be distributed (for example, a
database advisory lock or leased row), not only an in-process boolean. A
worker crash MUST release or expire the guard. Hosts MUST record round
duration, skipped-round count, due-job lag, and claimed-job count. Persistent
skips are overload and SHOULD trigger operational warning/backpressure.

- Delivery is **at least once**. Handlers MUST be idempotent using `job_id` or
  a domain idempotency key in the payload.
- The Host MUST lease/claim atomically so concurrent scheduler workers cannot
  deliver the same attempt concurrently.
- A crashed worker's lease MUST expire and become eligible for redelivery.
- The Host MUST persist jobs before acknowledging an upsert.
- Terminal states are `completed`, `cancelled`, and `dead_letter`; active
  states are `pending` and `leased`.
- Hosts SHOULD apply bounded exponential backoff with jitter unless the AIPP
  supplies a valid future `retry_at`.

## 6. Security and execution boundary

The Host MUST verify that the requested handler was registered by the calling
AIPP. It MUST reject cross-app job access and payloads above its configured
size limit. Payloads and errors are untrusted data and MUST NOT be interpreted
as prompts, code, or tool instructions.

A scheduled callback invokes only the named AIPP business handler. Scheduling
does not grant permission to start an LLM agent loop, execute Host tools, or
expand function authority. Any such work remains subject to the ordinary AIPP
tool/skill and function-authority contracts.

## 7. Relationship to existing Host implementations

The original world-one scheduler already supplies durable storage, atomic due
claiming, listener registration, replacement by key, and cancellation. It is
an implementation precursor, not the public AIPP contract: its Java types and
listener names are app-local and it marks jobs fired before callback success.

Host implementations should adapt that mechanism to the shared contracts,
add the app ownership dimension and leases/retries, and keep the scheduler
runtime in the Host. AIPP-specific listeners belong in their AIPP and register
through `GET /api/schedules`; they must not be copied into the Host.
