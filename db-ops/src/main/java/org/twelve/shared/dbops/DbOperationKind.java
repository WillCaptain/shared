package org.twelve.shared.dbops;

/**
 * Database operation categories for structured SQL logs.
 */
public enum DbOperationKind {
    QUERY,
    UPDATE,
    TRANSACTION
}
