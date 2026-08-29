# LLM Gateway SSE protocol 0.1

The request body is UTF-8 `application/json`; streaming is selected with
`Accept: text/event-stream`. The response is UTF-8 `text/event-stream`. Each event has one JSON
`data:` line.

1. The first event is `event: invocation`; its payload contains a non-empty `invocationId`.
2. Zero or more `content` and `reasoning` events follow and are forwarded without full buffering.
3. A reported final usage is emitted as `event: usage`.
4. Closing the downstream response cancels the upstream Provider response body.
5. Usage already reported before cancellation is durable and chargeable according to server policy.
6. Missing final usage is stored as `usage_unknown` / `reconciliation_pending` and is not charged.

Unknown event types and additive JSON properties must be ignored by compatible clients.
