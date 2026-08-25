package org.twelve.shared.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LLMCallerUsageTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void parsesNonStreamingUsageAndPublishesReportedEvent() throws Exception {
        List<UsageEvent> events = new CopyOnWriteArrayList<>();
        startServer("""
                {"id":"provider-call","choices":[{"finish_reason":"stop","message":{"content":"ok"}}],
                 "usage":{"prompt_tokens":100,"completion_tokens":25,
                 "prompt_tokens_details":{"cached_tokens":40}}}
                """.replaceAll("\\s+", ""));

        LLMCaller caller = new LLMCaller(config(), events::add, new NoopUsageCallGate())
                .withUsageContext(new UsageCallContext("u1", "s1", "t1", "execution",
                        "chat", "platform", true));
        LLMCaller.LLMResponse response = caller.callTextOnly(
                List.of(Map.of("role", "user", "content", "hello")), 32);

        assertThat(response.content()).isEqualTo("ok");
        assertThat(response.usage().usageStatus()).isEqualTo(Usage.REPORTED);
        assertThat(response.usage().inputTokens()).isEqualTo(100L);
        assertThat(response.usage().cachedInputTokens()).isEqualTo(40L);
        assertThat(response.usage().uncachedInputTokens()).isEqualTo(60L);
        assertThat(response.usage().outputTokens()).isEqualTo(25L);
        assertThat(response.usage().provider()).isEqualTo("test-provider");
        assertThat(response.usage().model()).isEqualTo("test-model");
        assertThat(response.usage().callId()).isNotBlank();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).usage()).isEqualTo(response.usage());
        assertThat(events.get(0).userId()).isEqualTo("u1");
        assertThat(events.get(0).billable()).isTrue();
    }

    @Test
    void parsesStreamingUsageChunkAfterChoicesAndPreservesSseCallbacks() throws Exception {
        List<UsageEvent> events = new CopyOnWriteArrayList<>();
        startServer("data: {\"choices\":[{\"delta\":{\"content\":\"hello\"},\"finish_reason\":null}]}\n\n"
                + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":7,"
                + "\"input_tokens_details\":{\"cached_tokens\":20}}}\n\n"
                + "data: [DONE]\n\n");

        LLMCaller caller = new LLMCaller(config(), events::add, new NoopUsageCallGate())
                .withUsageContext(new UsageCallContext("u1", "s1", "t2", "tool_followup",
                        "chat", "platform", true));
        List<String> text = new CopyOnWriteArrayList<>();
        LLMCaller.LLMResponse response = caller.callStream(
                List.of(Map.of("role", "user", "content", "hello")), "[]", 32, "auto",
                text::add, ignored -> {});

        assertThat(text).containsExactly("hello");
        assertThat(response.content()).isEqualTo("hello");
        assertThat(response.usage().usageStatus()).isEqualTo(Usage.REPORTED);
        assertThat(response.usage().inputTokens()).isEqualTo(120L);
        assertThat(response.usage().cachedInputTokens()).isEqualTo(20L);
        assertThat(response.usage().uncachedInputTokens()).isEqualTo(100L);
        assertThat(response.usage().outputTokens()).isEqualTo(7L);
        assertThat(response.usage().callType()).isEqualTo("tool_followup");
        assertThat(events).hasSize(1);
    }

    @Test
    void missingUsageIsUnknownAndNeverSilentlyZero() throws Exception {
        List<UsageEvent> events = new CopyOnWriteArrayList<>();
        startServer("{" +
                "\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"ok\"}}]" +
                "}");

        LLMCaller caller = new LLMCaller(config(), events::add, new NoopUsageCallGate());
        LLMCaller.LLMResponse response = caller.callTextOnly(
                List.of(Map.of("role", "user", "content", "hello")), 32);

        assertThat(response.usage().usageStatus()).isEqualTo(Usage.UNKNOWN);
        assertThat(response.usage().inputTokens()).isNull();
        assertThat(response.usage().cachedInputTokens()).isNull();
        assertThat(response.usage().uncachedInputTokens()).isNull();
        assertThat(response.usage().outputTokens()).isNull();
        assertThat(events).hasSize(1);
    }

    @Test
    void cachedTokensGreaterThanInputClampUncachedToZero() throws Exception {
        List<UsageEvent> events = new CopyOnWriteArrayList<>();
        startServer("{" +
                "\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"ok\"}}]," +
                "\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":1,\"cached_tokens\":120}" +
                "}");

        LLMCaller caller = new LLMCaller(config(), events::add, new NoopUsageCallGate());
        LLMCaller.LLMResponse response = caller.callTextOnly(
                List.of(Map.of("role", "user", "content", "hello")), 32);

        assertThat(response.usage().uncachedInputTokens()).isZero();
    }

    @Test
    void failedProviderCallWithoutUsagePublishesNoChargeableEvent() throws Exception {
        List<UsageEvent> events = new CopyOnWriteArrayList<>();
        startServer(500, "{\"error\":{\"message\":\"provider unavailable\"}}");
        LLMCaller caller = new LLMCaller(config(), events::add, new NoopUsageCallGate())
                .withUsageContext(UsageCallContext.platformCall("u1", "s1", "t1", "execution", "chat"));

        assertThatThrownBy(() -> caller.callTextOnly(
                List.of(Map.of("role", "user", "content", "hello")), 32))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("500");
        assertThat(events).isEmpty();
    }

    private LLMConfig config() {
        return LLMConfig.builder()
                .apiKey("test-key")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                .provider("test-provider")
                .model("test-model")
                .build();
    }

    private void startServer(String body) throws IOException {
        startServer(200, body);
    }

    private void startServer(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", body.startsWith("data:")
                    ? "text/event-stream" : "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }
}
