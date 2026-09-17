package org.twelve.aipp.invocation;

import com.sun.net.httpserver.*;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BoundedAuthorityHttpTest {
    HttpServer server; String origin;
    @BeforeEach void setup() throws Exception { server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); origin="http://127.0.0.1:"+server.getAddress().getPort(); }
    @AfterEach void cleanup() { server.stop(0); }
    BoundedAuthorityHttp client(Duration timeout,int max) { return new BoundedAuthorityHttp(origin,timeout,max); }
    @Test void exactBytesAndHeadersArePreserved() throws Exception {
        byte[] body="{\"value\":1}".getBytes();
        server.createContext("/api/evidence/authorize",x -> { assertArrayEquals(body,x.getRequestBody().readAllBytes()); assertEquals("proof",x.getRequestHeaders().getFirst("X-Proof")); x.getResponseHeaders().set("X-Response","signed"); x.sendResponseHeaders(200,body.length); x.getResponseBody().write(body); x.close(); }); server.start();
        var r=client(Duration.ofSeconds(2),100).send("POST","/api/evidence/authorize",Map.of("X-Proof","proof"),body);
        assertEquals(200,r.status()); assertArrayEquals(body,r.body()); assertEquals("signed",r.headers().firstValue("X-Response").orElseThrow());
    }
    @Test void redirectsNeverForwardCredentials() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        server.createContext("/api/evidence/authorize",x -> { x.getResponseHeaders().set("Location",origin+"/redirected"); x.sendResponseHeaders(302,-1); x.close(); });
        server.createContext("/redirected",x -> { calls.incrementAndGet(); x.sendResponseHeaders(200,-1); x.close(); }); server.start();
        assertEquals(302,client(Duration.ofSeconds(2),100).send("POST","/api/evidence/authorize",Map.of("Authorization","secret"),new byte[0]).status()); assertEquals(0,calls.get());
    }
    @Test void overlongResponseCancelsRead() throws Exception {
        server.createContext("/api/evidence/authorize",x -> { x.sendResponseHeaders(200,0); try { x.getResponseBody().write(new byte[10000]); } finally { x.close(); } }); server.start();
        assertThrows(SecurityException.class,() -> client(Duration.ofSeconds(2),100).send("POST","/api/evidence/authorize",Map.of(),new byte[0]));
    }
    @Test void deadlineCoversSlowResponseBody() throws Exception {
        server.createContext("/api/evidence/authorize",x -> { x.sendResponseHeaders(200,0); x.getResponseBody().write(1); x.getResponseBody().flush(); try { Thread.sleep(400); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } finally { x.close(); } }); server.start();
        long started=System.nanoTime();
        assertThrows(SecurityException.class,() -> client(Duration.ofMillis(100),100).send("POST","/api/evidence/authorize",Map.of(),new byte[0]));
        assertTrue(Duration.ofNanos(System.nanoTime()-started).toMillis()<1000);
    }
    @Test void rejectsUntrustedOriginsPathsMethodsAndOversizedRequests() {
        for(String url:List.of("http://example.com","https://u:p@example.com","https://example.com/path","https://example.com?q=1"))
            assertThrows(IllegalArgumentException.class,() -> new BoundedAuthorityHttp(url,Duration.ofSeconds(1),100));
        var c=client(Duration.ofSeconds(1),100);
        assertThrows(IllegalArgumentException.class,() -> c.send("POST","/api/evidence/authorize?x=1",Map.of(),new byte[0]));
        assertThrows(IllegalArgumentException.class,() -> c.send("PUT","/api/evidence/delegations",Map.of(),new byte[0]));
        assertThrows(IllegalArgumentException.class,() -> c.send("POST","/api/evidence/authorize",Map.of(),new byte[101]));
    }
}
