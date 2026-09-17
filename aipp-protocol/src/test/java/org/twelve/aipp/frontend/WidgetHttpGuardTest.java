package org.twelve.aipp.frontend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class WidgetHttpGuardTest {
    @Test void reportsDirectAndProxyHttpFailures() throws Exception {
        var host = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        host.createContext("/api/widgets", ex -> respond(ex, 200,
                "{\"widgets\":[{\"app_id\":\"sample\",\"render\":{\"kind\":\"esm\",\"url\":\"/widget.js\"}}]}"));
        host.createContext("/widget.js", ex -> respond(ex, 404, "missing"));
        host.createContext("/api/proxy/app/sample/widget.js", ex -> respond(ex, 401, "unauthorized"));
        host.start();
        try {
            var result = WidgetGuardSupport.checkAllWidgetsViaWorldOne(url(host));
            assertFalse(result.skipped());
            assertEquals(2, result.failures().size());
            assertTrue(result.failures().stream().anyMatch(s -> s.contains("(direct)") && s.contains("HTTP 404")));
            assertTrue(result.failures().stream().anyMatch(s -> s.contains("(host-proxy)") && s.contains("HTTP 401")));
        } finally { host.stop(0); }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }

    private static String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test void loadsBothPathsPreservesQueryAndDoesNotLeakHostCredentials() throws Exception {
        var app = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var host = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var directAuth = new AtomicReference<String>();
        var directCalls = new AtomicInteger();
        var proxyCalls = new AtomicInteger();
        app.createContext("/widget.js", ex -> {
            directAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            directCalls.incrementAndGet();
            respond(ex, 200, "export function mount() {}");
        });
        host.createContext("/api/widgets", ex -> {
            if (!"Bearer fixture".equals(ex.getRequestHeaders().getFirst("Authorization"))) {
                respond(ex, 401, "{}"); return;
            }
            respond(ex, 200, "{\"widgets\":[{\"app_id\":\"sample\",\"type\":\"sample.view\","
                    + "\"render\":{\"kind\":\"esm\",\"url\":\"" + url(app) + "/widget.js?v=2\"}}]}");
        });
        host.createContext("/api/proxy/app/sample/widget.js", ex -> {
            proxyCalls.incrementAndGet();
            boolean valid = "Bearer fixture".equals(ex.getRequestHeaders().getFirst("Authorization"))
                    && "v=2".equals(ex.getRequestURI().getRawQuery());
            respond(ex, valid ? 200 : 401, "export function mount() {}");
        });
        app.start(); host.start();
        try {
            var result = WidgetGuardSupport.checkAllWidgetsViaWorldOne(url(host), Map.of("Authorization", "Bearer fixture"));
            assertFalse(result.skipped());
            assertEquals(java.util.List.of(), result.failures());
            assertEquals(1, directCalls.get());
            assertEquals(1, proxyCalls.get());
            assertNull(directAuth.get(), "Host token must not reach the AIPP origin");
            var unauthorized = WidgetGuardSupport.checkAllWidgetsViaWorldOne(url(host));
            assertFalse(unauthorized.skipped(), "401 is a failure, not an unavailable-test skip");
            assertTrue(unauthorized.failures().toString().contains("HTTP 401"));
        } finally { host.stop(0); app.stop(0); }
    }
}
