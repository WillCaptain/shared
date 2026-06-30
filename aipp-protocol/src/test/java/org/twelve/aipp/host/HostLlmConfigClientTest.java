package org.twelve.aipp.host;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HostLlmConfigClientTest {

    private HttpServer server;
    private int port;
    private volatile String lastPath;
    private volatile String lastAuth;

    @BeforeEach
    void setUp() throws IOException {
        HostLlmConfigClient.clearCache();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/api/llm-config", exchange -> {
            lastPath = exchange.getRequestURI().getPath() + exchange.getRequestURI().getQuery();
            lastAuth = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] body = """
                    {
                      "ok": true,
                      "source": "instance",
                      "config": {
                        "api_key": "sk-test",
                        "base_url": "https://api.example.com/v1",
                        "model": "test-model",
                        "timeout_seconds": 90
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
        HostLlmConfigClient.clearCache();
    }

    @Test
    void fetch_returnsResolvedConfig() {
        HostLlmConfigClient.FetchResult r = HostLlmConfigClient.fetch(
                "http://127.0.0.1:" + port, "001", "tok");
        assertThat(r.status()).isEqualTo(HostLlmConfigClient.Status.OK);
        assertThat(r.config()).isNotNull();
        assertThat(r.config().apiKey()).isEqualTo("sk-test");
        assertThat(r.config().model()).isEqualTo("test-model");
        assertThat(r.config().timeoutSeconds()).isEqualTo(90);
        assertThat(lastPath).contains("user_id=001");
        assertThat(lastAuth).isEqualTo("Bearer tok");
    }

    @Test
    void fetch_notConfiguredWhenOkFalse() throws IOException {
        server.stop(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/api/llm-config", exchange -> {
            byte[] body = """
                    {"ok": false, "error": "llm_not_configured", "message": "none"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        HostLlmConfigClient.FetchResult r = HostLlmConfigClient.fetch(
                "http://127.0.0.1:" + port, "001", "");
        assertThat(r.status()).isEqualTo(HostLlmConfigClient.Status.NOT_CONFIGURED);
        assertThat(r.error()).isEqualTo("llm_not_configured");
    }
}
