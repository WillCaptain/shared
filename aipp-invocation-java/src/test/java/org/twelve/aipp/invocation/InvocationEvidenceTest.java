package org.twelve.aipp.invocation;
import org.junit.jupiter.api.*;
import org.twelve.aipp.identity.InvocationRequest;
import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class InvocationEvidenceTest {
    static KeyPair keys;
    final Clock clock = Clock.fixed(Instant.parse("2026-09-14T00:00:00Z"), ZoneOffset.UTC);
    Map<String, InvocationEvidence.Trust> trust;
    @BeforeAll static void keys() throws Exception { var g = KeyPairGenerator.getInstance("RSA"); g.initialize(2048); keys = g.generateKeyPair(); }
    @BeforeEach void trust() { trust = Map.of("key", new InvocationEvidence.Trust("issuer", (RSAPublicKey) keys.getPublic())); }
    InvocationRequest request(String app, String op, String target, String body) {
        return new InvocationRequest(app, op, "POST", target, body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    InvocationRequest request() { return request("sample-app", "write", "/api/tools/write?env=test", "{}"); }
    String issue(InvocationRequest r) { return InvocationEvidence.issue("issuer", "key", (RSAPrivateKey) keys.getPrivate(), "alice", "org", r, clock); }
    @Test void twoUnrelatedAppsUseSameVerifier() {
        for (String app : List.of("sample-app", "another-app")) {
            var r = request(app, "write", "/api/tools/write", "{}");
            assertEquals("alice", InvocationEvidence.verify(issue(r), r, trust, clock, (a,b,c,d) -> true).subject());
        }
    }
    @Test void requestSubstitutionFailsBeforeClaim() {
        var claims = new AtomicInteger(); String token = issue(request());
        for (var r : List.of(request("other-app", "write", "/api/tools/write?env=test", "{}"),
                request("sample-app", "remove", "/api/tools/write?env=test", "{}"),
                request("sample-app", "write", "/api/tools/write?env=prod", "{}"),
                request("sample-app", "write", "/api/tools/write?env=test", "{ }")))
            assertThrows(SecurityException.class, () -> InvocationEvidence.verify(token, r, trust, clock, (a,b,c,d) -> { claims.incrementAndGet(); return true; }));
        assertEquals(0, claims.get());
    }
    @Test void expiryFutureIssueAndUnknownKeyFail() {
        String token = issue(request());
        for (int shift : List.of(60, -6))
            assertThrows(SecurityException.class, () -> InvocationEvidence.verify(token, request(), trust, Clock.offset(clock, Duration.ofSeconds(shift)), (a,b,c,d) -> true));
        assertThrows(SecurityException.class, () -> InvocationEvidence.verify(token, request(), Map.of(), clock, (a,b,c,d) -> true));
    }
    @Test void replayAndReplayStoreOutageFailClosed() {
        String token = issue(request()); var seen = new HashSet<String>();
        InvocationEvidence.ReplayStore replay = (a,b,c,d) -> seen.add(a + ":" + b + ":" + c);
        InvocationEvidence.verify(token, request(), trust, clock, replay);
        assertThrows(SecurityException.class, () -> InvocationEvidence.verify(token, request(), trust, clock, replay));
        assertThrows(SecurityException.class, () -> InvocationEvidence.verify(issue(request()), request(), trust, clock, (a,b,c,d) -> { throw new IllegalStateException("offline"); }));
    }
    @Test void tamperedAndMalformedEvidenceHaveSanitizedErrors() {
        String[] p = issue(request()).split("\\.");
        String changed = p[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString("secret".getBytes()) + "." + p[2];
        for (String token : List.of("secret", changed, "x".repeat(16385))) {
            var e = assertThrows(SecurityException.class, () -> InvocationEvidence.verify(token, request(), trust, clock, (a,b,c,d) -> true));
            assertEquals("invocation_identity_invalid", e.getMessage()); assertNull(e.getCause());
        }
    }
    String resign(String payload) throws Exception {
        var original = com.nimbusds.jose.JWSObject.parse(issue(request()));
        var next = new com.nimbusds.jose.JWSObject(original.getHeader(), new com.nimbusds.jose.Payload(payload));
        next.sign(new com.nimbusds.jose.crypto.RSASSASigner((RSAPrivateKey) keys.getPrivate())); return next.serialize();
    }
    @Test void validSignatureCannotBypassVersionIssuerOrLifetimeChecks() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String original = com.nimbusds.jose.JWSObject.parse(issue(request())).getPayload().toString();
        for (String field : List.of("version", "issuer", "subject")) {
            var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(original);
            node.put(field, field.equals("subject") ? "" : "untrusted"); String token = resign(node.toString());
            assertThrows(SecurityException.class, () -> InvocationEvidence.verify(token, request(), trust, clock, (a,b,c,d) -> true));
        }
        var longTtl = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(original);
        longTtl.put("expires_at", clock.instant().getEpochSecond() + 61); String token = resign(longTtl.toString());
        assertThrows(SecurityException.class, () -> InvocationEvidence.verify(token, request(), trust, clock, (a,b,c,d) -> true));
    }
    @Test void signedDuplicateFieldsAreRejected() throws Exception {
        String original = com.nimbusds.jose.JWSObject.parse(issue(request())).getPayload().toString();
        String token = resign(original.substring(0, original.length() - 1) + ",\"subject\":\"mallory\"}");
        assertThrows(SecurityException.class, () -> InvocationEvidence.verify(token, request(), trust, clock, (a,b,c,d) -> true));
    }
}
