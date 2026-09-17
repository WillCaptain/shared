package org.twelve.aipp.invocation;

import org.twelve.shared.dbops.AtomicDbOps;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/** Consumer-owned schema, generic storage. Invoke before entering a business transaction. */
public final class PostgresInvocationReplayStore implements InvocationEvidence.ReplayStore {
    private final AtomicDbOps db;
    private final String table;

    public PostgresInvocationReplayStore(AtomicDbOps db, String schema) {
        this.db = Objects.requireNonNull(db);
        if (schema == null || !schema.matches("[a-z][a-z0-9_]{0,62}") || schema.startsWith("pg_")
                || schema.equals("information_schema") || schema.equals("public"))
            throw new IllegalArgumentException("Consumer-owned schema required");
        table = "\"" + schema + "\".invocation_replay_claims";
    }

    /** Explicit startup operation, never automatic in the request path. */
    public void initialize() {
        String schema = table.substring(0, table.indexOf('.'));
        db.execute("invocation_replay.schema", "CREATE SCHEMA IF NOT EXISTS " + schema);
        db.execute("invocation_replay.table", "CREATE TABLE IF NOT EXISTS " + table
                + " (claim_key CHAR(64) PRIMARY KEY, expires_at TIMESTAMPTZ NOT NULL, claimed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }

    @Override public boolean claim(String issuer, String audience, String invocationId, Instant expiresAt) {
        Objects.requireNonNull(expiresAt, "Expiry required");
        String key = claimKey(issuer, audience, invocationId);
        // Joining a caller transaction would let rollback erase the anti-replay claim.
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Replay verification must precede business transactions");
        return db.inTransaction("invocation_replay.claim", () -> {
            if (!TransactionSynchronizationManager.isActualTransactionActive())
                throw new IllegalStateException("Replay store requires a transaction-enabled AtomicDbOps");
            return db.update("invocation_replay.insert", "INSERT INTO " + table
                    + " (claim_key,expires_at) SELECT ?,? WHERE ? > clock_timestamp() ON CONFLICT (claim_key) DO NOTHING",
                    key, Timestamp.from(expiresAt), Timestamp.from(expiresAt)) == 1;
        });
    }

    /** Bounded maintenance; database time also fences all new claims, including under clock skew. */
    public int purgeExpired(int limit) {
        if(limit<1 || limit>1000) throw new IllegalArgumentException("Invalid replay purge limit");
        return db.inTransaction("invocation_replay.purge", () -> db.update("invocation_replay.delete_expired",
                "DELETE FROM " + table + " WHERE claim_key IN (SELECT claim_key FROM " + table
                + " WHERE expires_at<=clock_timestamp() ORDER BY expires_at LIMIT ? FOR UPDATE SKIP LOCKED)", limit));
    }

    private static String claimKey(String... parts) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                if (part == null || part.isBlank() || part.length() > 8192) throw new IllegalArgumentException("Invalid replay identity");
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
