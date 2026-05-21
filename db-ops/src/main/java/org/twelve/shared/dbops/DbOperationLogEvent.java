package org.twelve.shared.dbops;

import java.time.Instant;
import java.util.List;

/**
 * Immutable event emitted by {@link AtomicDbOps}.
 */
public record DbOperationLogEvent(
        Instant timestamp,
        DbOperationKind kind,
        String opName,
        String sql,
        List<Object> args,
        Long elapsedMs,
        Integer affectedRows,
        boolean success,
        String error
) {}
