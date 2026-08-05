package org.twelve.shared.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAiCompatibleEmbeddingProviderTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void batchesRequestsAndRestoresResponseIndexOrder() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            requests.incrementAndGet();
            JsonNode request = JSON.readTree(exchange.getRequestBody());
            assertEquals("test-model", request.path("model").asText());
            assertEquals("Bearer secret", exchange.getRequestHeaders().getFirst("Authorization"));
            JsonNode inputs = request.path("input");
            StringBuilder body = new StringBuilder("{\"data\":[");
            for (int i = inputs.size() - 1; i >= 0; i--) {
                if (i < inputs.size() - 1) body.append(',');
                body.append("{\"index\":").append(i).append(",\"embedding\":[")
                        .append(inputs.get(i).asText().length()).append(",").append(i).append("]}");
            }
            body.append("]}");
            write(exchange, 200, body.toString());
        });
        server.start();

        URI endpoint = URI.create("http://localhost:" + server.getAddress().getPort() + "/v1/embeddings");
        EmbeddingProvider provider =
                new OpenAiCompatibleEmbeddingProvider(endpoint, "secret", "test-model", 2);

        List<float[]> vectors = provider.embed(List.of("a", "bb", "ccc", "dddd", "eeeee"));

        assertEquals(3, requests.get());
        assertEquals(5, vectors.size());
        assertArrayEquals(new float[]{1, 0}, vectors.get(0));
        assertArrayEquals(new float[]{3, 0}, vectors.get(2));
        assertArrayEquals(new float[]{5, 0}, vectors.get(4));
    }

    private static void write(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
