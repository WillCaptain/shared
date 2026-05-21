package org.twelve.shared.dbops;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Shared, atomic DB operation facade.
 *
 * <p>Provides:
 * <ul>
 *   <li>transaction wrapper ({@link #inTransaction})</li>
 *   <li>query/update helpers</li>
 *   <li>structured SQL operation logging</li>
 * </ul>
 */
public final class AtomicDbOps {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final DbOperationLogSink sink;

    public AtomicDbOps(JdbcTemplate jdbc, TransactionTemplate tx, DbOperationLogSink sink) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.sink = sink;
    }

    public <T> T inTransaction(String opName, Supplier<T> body) {
        long t0 = System.currentTimeMillis();
        try {
            T out = tx.execute(status -> body.get());
            emit(DbOperationKind.TRANSACTION, opName, null, List.of(),
                    System.currentTimeMillis() - t0, null, true, null);
            return out;
        } catch (RuntimeException e) {
            emit(DbOperationKind.TRANSACTION, opName, null, List.of(),
                    System.currentTimeMillis() - t0, null, false, e.getMessage());
            throw e;
        }
    }

    public int update(String opName, String sql, Object... args) {
        long t0 = System.currentTimeMillis();
        List<Object> argv = Arrays.asList(args == null ? new Object[0] : args);
        try {
            int rows = jdbc.update(sql, args);
            emit(DbOperationKind.UPDATE, opName, sql, argv,
                    System.currentTimeMillis() - t0, rows, true, null);
            return rows;
        } catch (RuntimeException e) {
            emit(DbOperationKind.UPDATE, opName, sql, argv,
                    System.currentTimeMillis() - t0, null, false, e.getMessage());
            throw e;
        }
    }

    public void execute(String opName, String sql) {
        long t0 = System.currentTimeMillis();
        try {
            jdbc.execute(sql);
            emit(DbOperationKind.UPDATE, opName, sql, List.of(),
                    System.currentTimeMillis() - t0, null, true, null);
        } catch (RuntimeException e) {
            emit(DbOperationKind.UPDATE, opName, sql, List.of(),
                    System.currentTimeMillis() - t0, null, false, e.getMessage());
            throw e;
        }
    }

    public <T> List<T> query(String opName, String sql, RowMapper<T> mapper, Object... args) {
        long t0 = System.currentTimeMillis();
        List<Object> argv = Arrays.asList(args == null ? new Object[0] : args);
        try {
            List<T> rows = jdbc.query(sql, mapper, args);
            emit(DbOperationKind.QUERY, opName, sql, argv,
                    System.currentTimeMillis() - t0, rows != null ? rows.size() : 0, true, null);
            return rows;
        } catch (RuntimeException e) {
            emit(DbOperationKind.QUERY, opName, sql, argv,
                    System.currentTimeMillis() - t0, null, false, e.getMessage());
            throw e;
        }
    }

    private void emit(DbOperationKind kind, String opName, String sql, List<Object> args,
                      long elapsedMs, Integer affectedRows, boolean success, String error) {
        if (sink == null) return;
        sink.log(new DbOperationLogEvent(
                Instant.now(),
                kind,
                opName,
                sql,
                args,
                elapsedMs,
                affectedRows,
                success,
                error
        ));
    }

    public <T> T queryForObjectNullable(String opName, String sql, Class<T> requiredType, Object... args) {
        long t0 = System.currentTimeMillis();
        List<Object> argv = Arrays.asList(args == null ? new Object[0] : args);
        try {
            T value = jdbc.queryForObject(sql, requiredType, args);
            emit(DbOperationKind.QUERY, opName, sql, argv,
                    System.currentTimeMillis() - t0, value == null ? 0 : 1, true, null);
            return value;
        } catch (RuntimeException e) {
            emit(DbOperationKind.QUERY, opName, sql, argv,
                    System.currentTimeMillis() - t0, null, false, e.getMessage());
            throw e;
        }
    }
}
