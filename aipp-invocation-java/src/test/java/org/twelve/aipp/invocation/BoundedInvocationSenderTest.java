package org.twelve.aipp.invocation;

import com.sun.net.httpserver.*;
import org.junit.jupiter.api.*;
import org.twelve.aipp.identity.AippInvocationIdentityContract;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BoundedInvocationSenderTest {
    HttpServer server; String origin; final byte[] BODY="frozen bytes".getBytes(); final Clock clock=Clock.systemUTC();
    @BeforeEach void setup() throws Exception { server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); origin="http://127.0.0.1:"+server.getAddress().getPort(); }
    @AfterEach void cleanup() { server.stop(0); }
    HttpRequest.Builder request() { return HttpRequest.newBuilder(URI.create(origin+"/api/tools/accept_evidence")).timeout(Duration.ofSeconds(1))
            .header(AippInvocationIdentityContract.EVIDENCE_HEADER,"test-proof").header("Content-Type","application/json").header("Accept","application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(BODY)); }
    BoundedInvocationSender.Response send(HttpRequest request) { return new BoundedInvocationSender().send(request,origin,"accept_evidence",BODY,clock.instant().plusSeconds(60),clock); }
    @Test void sendsExactBytesAndNeverFollowsRedirects() {
        AtomicInteger extra=new AtomicInteger();
        server.createContext("/api/tools/accept_evidence",x -> { assertArrayEquals(BODY,x.getRequestBody().readAllBytes()); x.getResponseHeaders().set("Location",origin+"/steal"); x.sendResponseHeaders(307,-1); x.close(); });
        server.createContext("/steal",x -> { extra.incrementAndGet(); x.close(); }); server.start();
        assertEquals(307,send(request().build()).status()); assertEquals(0,extra.get());
    }
    @Test void rejectsExtraCredentialsAndBodyOrDestinationMismatchBeforeSending() {
        assertThrows(BoundedInvocationSender.Failure.class,() -> send(request().header("Authorization","Bearer secret").build()));
        assertThrows(BoundedInvocationSender.Failure.class,() -> send(request().POST(HttpRequest.BodyPublishers.ofString("changed")).build()));
        assertThrows(BoundedInvocationSender.Failure.class,() -> send(request().uri(URI.create(origin+"/api/tools/other")).build()));
        assertThrows(BoundedInvocationSender.Failure.class,() -> new BoundedInvocationSender().send(request().build(),"http://example.com","accept_evidence",BODY,clock.instant().plusSeconds(60),clock));
    }
    @Test void refusesExpiredOrNearlyExpiredProof() {
        for(int seconds:List.of(-1,1,2)) assertThrows(BoundedInvocationSender.Failure.class,() -> new BoundedInvocationSender().send(request().build(),origin,"accept_evidence",BODY,clock.instant().plusSeconds(seconds),clock));
    }
    @Test void responseBodyLimitIsEnforcedDuringRead() {
        server.createContext("/api/tools/accept_evidence",x -> { x.sendResponseHeaders(202,0); try { x.getResponseBody().write(new byte[5000]); } finally { x.close(); } }); server.start();
        assertEquals("response_too_large",assertThrows(BoundedInvocationSender.Failure.class,() -> send(request().build())).code());
    }
    @Test void completeResponseDeadlineAndInterruptionCancelTransport() {
        server.createContext("/api/tools/accept_evidence",x -> { x.sendResponseHeaders(202,0); x.getResponseBody().write(1); x.getResponseBody().flush(); try { Thread.sleep(1300); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } finally { x.close(); } }); server.start();
        long started=System.nanoTime(); assertThrows(BoundedInvocationSender.Failure.class,() -> send(request().build()));
        assertTrue(Duration.ofNanos(System.nanoTime()-started).toMillis()<2500);
        Thread.currentThread().interrupt(); try { assertThrows(BoundedInvocationSender.Failure.class,() -> send(request().build())); assertTrue(Thread.currentThread().isInterrupted()); }
        finally { Thread.interrupted(); }
    }
}
