package org.twelve.aipp.host;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class AippHostLifecycleTest {

    private HttpServer server;
    private int port;
    private final AtomicInteger installCalls = new AtomicInteger();
    private final AtomicInteger deleteCalls = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/api/registry/install", exchange -> {
            installCalls.incrementAndGet();
            respond(exchange, 200, "{\"success\":true,\"message\":\"ok\"}");
        });
        server.createContext("/api/registry/", exchange -> {
            if ("DELETE".equals(exchange.getRequestMethod())) {
                deleteCalls.incrementAndGet();
                respond(exchange, 200, "{\"success\":true}");
            } else {
                respond(exchange, 404, "{}");
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private AippHostLifecycle newLifecycle() {
        return new AippHostLifecycle("http://127.0.0.1:" + port, "note-one", "http://127.0.0.1:9999");
    }

    private static void awaitUntil(Duration timeout, BooleanSupplier cond) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) return;
            try { Thread.sleep(20); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (!cond.getAsBoolean()) {
            throw new AssertionError("condition not met within " + timeout);
        }
    }

    @Test
    void register_postsInstallAndSucceeds() {
        AippHostLifecycle lc = newLifecycle();
        AippHostLifecycle.Result r = lc.register();
        assertThat(r.success()).isTrue();
        assertThat(installCalls.get()).isEqualTo(1);
    }

    @Test
    void attachLoop_keepsReattachingOnAShortInterval() {
        AippHostLifecycle lc = newLifecycle();
        lc.startAttachLoop(5, Duration.ofMillis(50), Duration.ofMillis(100));
        try {
            awaitUntil(Duration.ofSeconds(3), () -> lc.attached() && installCalls.get() >= 3);
        } finally {
            lc.stop();
        }
        assertThat(lc.attached()).isTrue();
        assertThat(installCalls.get()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void attachLoop_stopHaltsFurtherCalls() throws InterruptedException {
        AippHostLifecycle lc = newLifecycle();
        lc.startAttachLoop(5, Duration.ofMillis(50), Duration.ofMillis(100));
        awaitUntil(Duration.ofSeconds(2), () -> installCalls.get() >= 2);
        lc.stop();
        int after = installCalls.get();
        Thread.sleep(400);
        assertThat(installCalls.get()).isLessThanOrEqualTo(after + 1);
    }

    @Test
    void attachLoop_neverCallsDeregister() {
        AippHostLifecycle lc = newLifecycle();
        lc.startAttachLoop(5, Duration.ofMillis(50), Duration.ofMillis(100));
        awaitUntil(Duration.ofSeconds(2), () -> installCalls.get() >= 2);
        lc.stop();
        assertThat(deleteCalls.get()).isZero();
    }
}
