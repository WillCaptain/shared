# db-ops

Shared Java module for:

- atomic DB execution helpers (`AtomicDbOps`)
- structured SQL operation logs (`DbOperationLogEvent`)

Typical integration:

1. construct `AtomicDbOps` with `JdbcTemplate`, `TransactionTemplate`, and `Slf4jDbOperationLogSink`
2. replace direct `jdbc.update/query` calls with `atomicDbOps.update/query`
3. use `inTransaction` for multi-step atomic write flows
