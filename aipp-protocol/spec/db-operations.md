# DB Operations — shared `db-ops` SDK

> **Rule:** Every service in this ecosystem that persists data — AIPP apps **and the Host** — uses the shared **`db-ops`** SDK (`shared/db-ops`, `org.twelve.shared.dbops`). No JPA/Hibernate, no raw `JdbcTemplate` calls in app code, no per-app homegrown DB layers.  
> **Source of truth:** the SDK code itself — `shared/db-ops/src/main/java/org/twelve/shared/dbops/AtomicDbOps.java`. There is no JSON manifest for this; the contract is the Java API.  
> **Reference implementations:** `ones/decision-reactor` (`DbOpsConfig`, `ReactorDbSchemaInit`, `ReactorDbStore`); `ones/world-one` (`WorldOneDbOpsConfig`, `WorldOneDbSchemaInit`, `db/Jdbc*Db`); `ones/memory-one` (`MemoryOneDbOpsConfig`, `MemoryDbSchemaInit`, `MemoryDb`).

---

## 1. Why one way

- **Atomicity**: multi-step writes go through `inTransaction` — no half-written state.
- **Uniform structured logs**: every query/update/transaction emits one `DbOperationLogEvent` line (op name, SQL, args, elapsed ms, rows, success/error). Debugging any AIPP's DB behavior looks the same.
- **Auditability**: operation names (`{domain}.{action}`) make logs greppable per domain.

---

## 2. Dependency

```xml
<dependency>
  <groupId>org.example</groupId>
  <artifactId>db-ops</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
<!-- plus spring-boot-starter-jdbc and your JDBC driver (PostgreSQL in this ecosystem) -->
```

The SDK depends only on `spring-jdbc` / `spring-tx` / `slf4j-api` — the **consumer** provides the driver and DataSource.

---

## 3. API surface (`AtomicDbOps`)

| Method | Use |
|--------|-----|
| `new AtomicDbOps(JdbcTemplate, TransactionTemplate, DbOperationLogSink)` | Construct once as a Spring bean |
| `<T> T inTransaction(String opName, Supplier<T> body)` | Multi-step atomic write flow |
| `int update(String opName, String sql, Object... args)` | INSERT / UPDATE / DELETE; returns affected rows |
| `void execute(String opName, String sql)` | DDL (CREATE / ALTER) |
| `<T> List<T> query(String opName, String sql, RowMapper<T> mapper, Object... args)` | SELECT |
| `<T> T queryForObjectNullable(String opName, String sql, Class<T> type, Object... args)` | Single scalar |

Logging: `Slf4jDbOperationLogSink` emits one line per operation (`db-op ts=… kind=… op=… ok=… ms=… rows=… sql=… args=…`), INFO on success, WARN on failure. Event shape: `DbOperationLogEvent(timestamp, kind QUERY|UPDATE|TRANSACTION, opName, sql, args, elapsedMs, affectedRows, success, error)`.

---

## 4. Wiring (Spring)

```java
@Configuration
public class DbOpsConfig {
    @Bean
    AtomicDbOps atomicDbOps(JdbcTemplate jdbc, PlatformTransactionManager txManager) {
        return new AtomicDbOps(
                jdbc,
                new TransactionTemplate(txManager),
                new Slf4jDbOperationLogSink(LoggerFactory.getLogger("org.twelve.<app>.dbops")));
    }
}
```

Logger name: `org.twelve.{app}.dbops` — keeps each app's DB log stream filterable.

---

## 5. Schema conventions

| Convention | Rule |
|------------|------|
| **One schema per app** | PostgreSQL schema named after the app, snake_case (e.g. `decision_reactor`, `world_entitir`). Never write into another app's schema or the Host's tables. |
| **Idempotent init** | A `@Component` with `@PostConstruct` runs `CREATE SCHEMA IF NOT EXISTS` + `CREATE TABLE IF NOT EXISTS` via `dbOps.execute`. |
| **Additive migrations** | Column additions via `ALTER TABLE … ADD COLUMN IF NOT EXISTS` with defaults, in the same init component. No external migration tool. |
| **Dynamic sub-schemas** | An app managing N isolated datasets may create schemas at runtime via the same idempotent `dbOps.execute("…", "CREATE SCHEMA IF NOT EXISTS " + name)` pattern (world-entitir: one schema per world). Sanitize/derive `name` from internal ids, never from raw user input. |
| **Naming** | Tables/columns snake_case lowercase. |

```java
@Component
public class MyAppDbSchemaInit {
    @PostConstruct
    void init() {
        dbOps.execute("myapp_schema.create", "CREATE SCHEMA IF NOT EXISTS my_app");
        dbOps.execute("myapp_schema.create_table", """
                CREATE TABLE IF NOT EXISTS my_app.things ( ... )""");
        dbOps.execute("myapp_schema.add_new_col",
                "ALTER TABLE my_app.things ADD COLUMN IF NOT EXISTS new_col TEXT NOT NULL DEFAULT ''");
    }
}
```

---

## 6. Operation naming

`{domain}.{action}` — e.g. `reactor.list_all`, `reactor.save_meta`, `reactor_schema.create`. Lets `grep "reactor\."` isolate one domain's DB traffic in logs.

---

## 7. Datasource & env

- Datasource URL/credentials come from Spring config with env-var overrides (`${MY_APP_DB_URL:jdbc:postgresql://localhost:5432/…}`). **Never** put DB URL/credentials in AIPP `configuration.values` (same rule as Host URL — see [`configuration.md`](configuration.md) and [`host-injection.md`](host-injection.md)).
- Runtime `env` (`production` / `staging`) comes from Host bindings (`PUT /api/host/bindings`), not from configuration. When data is env-scoped, store env in a **column** and filter at read time (pattern: `published_env VARCHAR(20)` in `decision_reactor.reactors`) — do not maintain separate databases per env.

---

## 8. Current adoption (2026-06)

| Service | Persistence | Status |
|---------|-------------|--------|
| **world-entitir** (primary AIPP) | `db-ops` — `world_entitir` schema + **dynamic per-world schemas** (`DbSchemaInit`, `WorldSessionStore`, `WorldRegistryStore`, `WorldDraftStore`, …) | ✅ Reference for app-side patterns |
| decision-reactor | `db-ops` + `@PostConstruct` schema init (`decision_reactor` schema) | ✅ |
| world-one (Host) | `db-ops` — `ui_sessions` / `session_messages` / `world_events` via `db/Jdbc*Db` | ✅ Migrated from JPA 2026-06 |
| memory-one | `db-ops` — `memories` via `MemoryDb` (shares the Host DB) | ✅ Migrated from JPA 2026-06 |
| outline-aipp | None (stateless; excludes `DataSourceAutoConfiguration`) | ✅ Stateless apps need no DB at all |

Note: `sqlite-jdbc` in world-entitir's pom is **not** an exception to this spec — it is a runtime requirement of the upstream `ontology` library, not an app persistence path.

**Any new service with persistence must use `db-ops`.** A stateless AIPP should exclude `DataSourceAutoConfiguration` instead of carrying an unused datasource.

### Migration pattern (JPA → db-ops), proven on world-one / memory-one

1. Keep the entity class as a plain POJO (drop `jakarta.persistence` annotations; names/fields unchanged).
2. Replace the `JpaRepository` with a small interface exposing **exactly the methods callers use** (same names/signatures — call sites only change the injected type), implemented on `AtomicDbOps` with a `RowMapper`.
3. `save()` keeps JPA upsert semantics: UPDATE by id first, INSERT when 0 rows (generated-id tables: INSERT when id is null).
4. Schema init DDL must reproduce the Hibernate-created tables **exactly** (table/column/index names) so `IF NOT EXISTS` is a no-op on live databases — verify with `psql \d` before shipping.
5. Beans that query at startup (`@PostConstruct`) must `@DependsOn` the schema-init bean — Hibernate's implicit DDL-before-repositories ordering is gone.
6. Watch transitive deps: removing `spring-boot-starter-data-jpa` also removes Hibernate's byte-buddy; Mockito may need an explicit `net.bytebuddy:byte-buddy` pin.
