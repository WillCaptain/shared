package org.twelve.shared.dbops;

import org.slf4j.Logger;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * Default SQL log sink that writes one structured line per operation.
 */
public final class Slf4jDbOperationLogSink implements DbOperationLogSink {

    private final Logger logger;

    public Slf4jDbOperationLogSink(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void log(DbOperationLogEvent event) {
        if (event == null || logger == null) return;
        String ts = event.timestamp() != null
                ? DateTimeFormatter.ISO_INSTANT.format(event.timestamp())
                : "";
        String args = event.args() == null
                ? "[]"
                : event.args().stream().map(this::safe).collect(Collectors.joining(", ", "[", "]"));
        String message = "db-op ts=" + ts
                + " kind=" + safe(event.kind())
                + " op=" + safe(event.opName())
                + " ok=" + event.success()
                + " ms=" + event.elapsedMs()
                + " rows=" + event.affectedRows()
                + " sql=\"" + safe(event.sql()) + "\""
                + " args=" + args
                + (event.error() != null && !event.error().isBlank()
                        ? " error=\"" + safe(event.error()) + "\""
                        : "");
        if (event.success()) logger.info(message);
        else logger.warn(message);
    }

    private String safe(Object value) {
        if (value == null) return "null";
        String s = String.valueOf(value);
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}
