package org.twelve.aipp.invocation;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.twelve.aipp.identity.InvocationRequest;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.twelve.aipp.identity.AippInvocationIdentityContract.*;

class InvocationHttpRequestTest {
    private final Map<String,Object> requirement = Map.of(REQUIREMENT_FIELD, VERIFIED_REQUEST_V1);

    @Test void signedBytesSurviveRealHttpForTwoAppsAndReplayIsRejected() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var keys = generator.generateKeyPair(); var clock = Clock.systemUTC();
        var trust = Map.of("test-key", new InvocationEvidence.Trust("test-host", (RSAPublicKey) keys.getPublic()));
        var seen = java.util.concurrent.ConcurrentHashMap.<String>newKeySet();
        var accepted = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int status = 403;
            try {
                String app = exchange.getRequestURI().getPath().split("/")[1];
                var actual = new InvocationRequest(app, "write", exchange.getRequestMethod(),
                        exchange.getRequestURI().toASCIIString(), exchange.getRequestBody().readAllBytes());
                var identity = InvocationEvidence.verify(exchange.getRequestHeaders().getFirst(EVIDENCE_HEADER),
                        actual, trust, clock, (issuer,audience,id,expires) -> seen.add(issuer + ":" + audience + ":" + id));
                if ("alice".equals(identity.subject()) && exchange.getRequestHeaders().getFirst("Authorization") == null) {
                    accepted.incrementAndGet(); status = 204;
                }
            } catch (SecurityException invalid) { /* Generic rejection, no evidence logged. */ }
            exchange.sendResponseHeaders(status, -1); exchange.close();
        });
        server.start();
        try {
            URI origin = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
            for (String app : List.of("sample-app", "another-app")) {
                byte[] bytes = "{ \"name\": \"世界\" }\n".getBytes(StandardCharsets.UTF_8);
                var snapshot = new InvocationRequest(app, "write", "POST", "/" + app + "/write?q=%E4%B8%96&q=two+words", bytes);
                var request = InvocationHttpRequest.prepare(origin, requirement, snapshot, Duration.ofSeconds(3),
                        r -> InvocationEvidence.issue("test-host", "test-key", (RSAPrivateKey) keys.getPrivate(), "alice", null, r, clock));
                Arrays.fill(bytes, (byte) 0);
                assertEquals(204, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
                assertEquals(403, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
            }
            assertEquals(2, accepted.get());
        } finally { server.stop(0); }
    }

    @Test void invalidRoutingFailsBeforeIssuanceWithoutLeakingDetails() {
        var snapshot = new InvocationRequest("sample-app", "write", "POST", "/api/tools/write", new byte[0]);
        for (String origin : List.of("https://host/base", "https://user:secret@host", "https://host?secret", "https://host#secret", "file:///tmp/test")) {
            var failure = assertThrows(SecurityException.class, () -> InvocationHttpRequest.prepare(URI.create(origin),
                    requirement, snapshot, Duration.ofSeconds(3), r -> { fail("must not issue"); return null; }));
            assertEquals(IDENTITY_UNAVAILABLE, failure.getMessage()); assertNull(failure.getCause());
        }
    }

    @Test void legacyRequestHasNoEvidenceAndProtectedRequestRequiresIssuer() {
        var snapshot = new InvocationRequest("sample-app", "read", "GET", "/api/tools/read", new byte[0]);
        var origin = URI.create("https://example.invalid/");
        var request = InvocationHttpRequest.prepare(origin, Map.of(), snapshot, Duration.ofSeconds(3), null);
        assertTrue(request.headers().firstValue(EVIDENCE_HEADER).isEmpty());
        assertTrue(request.headers().firstValue("Authorization").isEmpty());
        assertThrows(SecurityException.class, () -> InvocationHttpRequest.prepare(origin, requirement, snapshot, Duration.ofSeconds(3), null));
    }
}
