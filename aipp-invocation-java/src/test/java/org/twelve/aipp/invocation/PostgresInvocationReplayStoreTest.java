package org.twelve.aipp.invocation;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.twelve.shared.dbops.AtomicDbOps;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="AIPP_REPLAY_TEST_URL", matches="jdbc:postgresql://localhost:5432/entitir_recovery_verify_[0-9]+")
class PostgresInvocationReplayStoreTest {
    DriverManagerDataSource source;
    TransactionTemplate tx;
    String schema;
    PostgresInvocationReplayStore store;
    AtomicDbOps db() { return new AtomicDbOps(new JdbcTemplate(source), tx, e -> {}); }
    @BeforeEach void setup() {
        source = new DriverManagerDataSource(System.getenv("AIPP_REPLAY_TEST_URL"), System.getProperty("user.name"), "");
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        schema = "replay_verify_" + UUID.randomUUID().toString().replace("-", "");
        store = new PostgresInvocationReplayStore(db(), schema); store.initialize(); store.initialize();
    }
    @Test void competingConnectionsHaveOneWinnerAndReopenRejectsReplay() throws Exception {
        var start = new CountDownLatch(1); var deadline = Instant.now().plusSeconds(60);
        try (var pool = Executors.newFixedThreadPool(8)) {
            var jobs = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 8; i++) jobs.add(pool.submit(() -> {
                start.await(); return new PostgresInvocationReplayStore(db(), schema).claim("issuer", "app", "id", deadline);
            }));
            start.countDown(); int winners = 0;
            for (var job : jobs) if (job.get(10, TimeUnit.SECONDS)) winners++;
            assertEquals(1, winners);
        }
        assertFalse(new PostgresInvocationReplayStore(db(), schema).claim("issuer", "app", "id", deadline));
    }
    @Test void scopeAndTupleEncodingAreUnambiguous() {
        var deadline = Instant.now().plusSeconds(60);
        assertTrue(store.claim("a:b", "c", "id", deadline));
        assertTrue(store.claim("a", "b:c", "id", deadline));
        assertTrue(store.claim("a:b", "different", "id", deadline));
        assertFalse(store.claim("a:b", "c", "id", deadline.plusSeconds(60)));
    }
    @Test void existingBusinessTransactionIsRejectedAndLaterRollbackDoesNotReleaseClaim() {
        var deadline = Instant.now().plusSeconds(60);
        tx.executeWithoutResult(status -> {
            assertThrows(IllegalStateException.class, () -> store.claim("issuer", "app", "inside", deadline));
            status.setRollbackOnly();
        });
        assertTrue(store.claim("issuer", "app", "inside", deadline));
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> { throw new IllegalStateException("business failure"); }));
        assertFalse(new PostgresInvocationReplayStore(db(), schema).claim("issuer", "app", "inside", deadline));
    }
    @Test void expiredClaimsAndUnavailableStorageFailClosed() {
        assertFalse(store.claim("issuer", "app", "expired", Instant.now().minusSeconds(1)));
        var uninitialized = new PostgresInvocationReplayStore(db(), "absent_" + UUID.randomUUID().toString().replace("-", ""));
        assertThrows(RuntimeException.class, () -> uninitialized.claim("issuer", "app", "id", Instant.now().plusSeconds(60)));
    }
    @Test void schemaMustBeConsumerOwnedAndNotSql() {
        for (String invalid : List.of("public", "pg_catalog", "information_schema", "x; DROP TABLE y", "a.b"))
            assertThrows(IllegalArgumentException.class, () -> new PostgresInvocationReplayStore(db(), invalid));
    }
    @Test void signedEvidenceIsOneUseAcrossVerifierInstances() throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var keys = generator.generateKeyPair(); var clock = java.time.Clock.systemUTC();
        var request = new org.twelve.aipp.identity.InvocationRequest("sample-app", "write", "POST", "/api/tools/write", new byte[0]);
        String token = InvocationEvidence.issue("issuer", "key", (java.security.interfaces.RSAPrivateKey) keys.getPrivate(), "alice", null, request, clock);
        var trust = Map.of("key", new InvocationEvidence.Trust("issuer", (java.security.interfaces.RSAPublicKey) keys.getPublic()));
        assertEquals("alice", InvocationEvidence.verify(token, request, trust, clock, store).subject());
        var reopened = new PostgresInvocationReplayStore(db(), schema);
        assertThrows(SecurityException.class, () -> InvocationEvidence.verify(token, request, trust, clock, reopened));
    }
}
