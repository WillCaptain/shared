package org.twelve.shared.dbops;

/**
 * Sink for structured database operation events.
 */
public interface DbOperationLogSink {
    void log(DbOperationLogEvent event);
}
