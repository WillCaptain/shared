package org.twelve.llmgateway.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.twelve.llmgateway.contract.*;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LlmGatewayClientTest {
    @Test void sendsIndependentServiceAndOpaqueDelegationCredentials() throws Exception {
        AtomicReference<com.sun.net.httpserver.Headers> headers = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/llm/chat/invocations", exchange -> {
            headers.set(exchange.getRequestHeaders());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"invocationId\":\"inv-1\",\"finishReason\":\"stop\",\"content\":\"ok\",\"toolCalls\":[],\"rawAssistantMessage\":{}}".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var client = new LlmGatewayClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    "note-one", "service-secret", (operation, metadata) -> "opaque.jwt.value");
            var result = client.chat(ChatInvocationRequest.text(
                    List.of(Map.of("role", "user", "content", "hello")), 16),
                    new GatewayRequestMetadata("s", "t", "f", "knowledge", "job-42"));
            assertEquals("inv-1", result.invocationId());
            assertEquals("note-one", headers.get().getFirst(GatewayProtocol.SERVICE_HEADER));
            assertEquals("service-secret", headers.get().getFirst(GatewayProtocol.SERVICE_CREDENTIAL_HEADER));
            assertEquals("opaque.jwt.value", headers.get().getFirst(GatewayProtocol.DELEGATION_HEADER));
            assertFalse(body.get().contains("userId")); assertFalse(body.get().contains("user_id"));
            assertTrue(body.get().contains("\"job_id\":\"job-42\""));
        } finally { server.stop(0); }
    }

    @Test void publicClientApiContainsNoSigningCapability() {
        assertTrue(Arrays.stream(LlmGatewayClient.class.getMethods())
                .noneMatch(method -> method.getName().matches("(?i).*(issue|sign|mint).*")));
        assertTrue(Arrays.stream(GatewayCredentialProvider.class.getMethods())
                .noneMatch(method -> method.getName().matches("(?i).*(issue|sign|mint).*")));
    }
}
