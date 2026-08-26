package org.twelve.shared.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared LLM HTTP infrastructure for OpenAI-compatible Chat Completions.
 */
public final class LLMCaller {

    public static final int DEFAULT_MAX_TOKENS_TOOLS     = 4096;
    public static final int DEFAULT_MAX_TOKENS_TEXT_ONLY = 2048;

    private static final String FINISH_TOOL_CALLS = "tool_calls";
    private static final String FINISH_STOP       = "stop";

    private static final Pattern FINISH_REASON_PAT =
            Pattern.compile("\"finish_reason\"\\s*:\\s*\"([^\"]+)\"");

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final LLMConfig config;
    private final HttpClient httpClient;
    private final UsageObserver usageObserver;
    private final UsageCallGate usageCallGate;
    private final UsageCallContext usageContext;

    public LLMCaller(LLMConfig config) {
        this(config, new NoopUsageObserver(), new NoopUsageCallGate(), UsageCallContext.none());
    }

    /** Host integration constructor; the shared module depends only on observer interfaces. */
    public LLMCaller(LLMConfig config, UsageObserver usageObserver, UsageCallGate usageCallGate) {
        this(config, usageObserver, usageCallGate, UsageCallContext.none());
    }

    private LLMCaller(LLMConfig config,
                      UsageObserver usageObserver,
                      UsageCallGate usageCallGate,
                      UsageCallContext usageContext) {
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.usageObserver = usageObserver == null ? new NoopUsageObserver() : usageObserver;
        this.usageCallGate = usageCallGate == null ? new NoopUsageCallGate() : usageCallGate;
        this.usageContext = usageContext == null ? UsageCallContext.none() : usageContext;
    }

    /** Return an immutable caller carrying metadata for the next provider invocation. */
    public LLMCaller withUsageContext(UsageCallContext context) {
        return new LLMCaller(config, usageObserver, usageCallGate,
                context == null ? UsageCallContext.none() : context);
    }

    public boolean hasKey() {
        return config.hasKey();
    }

    /** True when this endpoint can't do SSE streaming, so callStream() must fall back to a single
     *  non-streaming call. Auto-detects Google's Gemini compat endpoint; overridable with LLM_NO_STREAM. */
    private boolean nonStreamingFallback() {
        String force = System.getenv("LLM_NO_STREAM");
        if (force != null) return "on".equalsIgnoreCase(force) || "true".equalsIgnoreCase(force) || "1".equals(force);
        String url = config.chatCompletionsUrl();
        return url != null && url.contains("generativelanguage.googleapis.com");
    }

    public LLMResponse call(List<Map<String, Object>> messages,
                            String toolsJson) throws Exception {
        return call(messages, toolsJson, DEFAULT_MAX_TOKENS_TOOLS, "auto");
    }

    public LLMResponse call(List<Map<String, Object>> messages,
                            String toolsJson,
                            int maxTokens) throws Exception {
        return call(messages, toolsJson, maxTokens, "auto");
    }

    public LLMResponse call(List<Map<String, Object>> messages,
                            String toolsJson,
                            int maxTokens,
                            String toolChoice) throws Exception {
        return call(messages, toolsJson, maxTokens, toolChoice, 0.1);
    }

    public LLMResponse call(List<Map<String, Object>> messages,
                            String toolsJson,
                            int maxTokens,
                            String toolChoice,
                            double temperature) throws Exception {
        usageCallGate.beforeCall(usageContext, config);
        String callId = UUID.randomUUID().toString();
        String body = buildBody(messages, toolsJson, maxTokens, toolChoice, temperature);
        LLMResponse response = parseResponse(send(body));
        return publishUsage(response, callId, true);
    }

    public LLMResponse callTextOnly(List<Map<String, Object>> messages) throws Exception {
        return callTextOnly(messages, DEFAULT_MAX_TOKENS_TEXT_ONLY);
    }

    public LLMResponse callTextOnly(List<Map<String, Object>> messages,
                                    int maxTokens) throws Exception {
        return callTextOnly(messages, maxTokens, 0.1);
    }

    public LLMResponse callTextOnly(List<Map<String, Object>> messages,
                                    int maxTokens,
                                    double temperature) throws Exception {
        usageCallGate.beforeCall(usageContext, config);
        String callId = UUID.randomUUID().toString();
        String body = buildTextOnlyBody(messages, maxTokens, temperature);
        LLMResponse response = parseResponse(send(body));
        return publishUsage(response, callId, true);
    }

    public LLMResponse callStream(List<Map<String, Object>> messages,
                                  String toolsJson, int maxTokens, String toolChoice,
                                  Consumer<String> textTokenCallback,
                                  Consumer<String> thinkingBatchCallback) throws Exception {
        // Some OpenAI-compatible endpoints don't support SSE streaming (notably Google's Gemini
        // generativelanguage compat endpoint, which drops the connection on "stream":true). For those we
        // make a single NON-streaming call and replay its content/reasoning through the same callbacks, so
        // the orchestrator sees an identical LLMResponse. Auto-detected by URL; forceable via LLM_NO_STREAM=on.
        if (nonStreamingFallback()) {
            LLMResponse r = call(messages, toolsJson, maxTokens, toolChoice);
            if (r.reasoning() != null && !r.reasoning().isEmpty() && thinkingBatchCallback != null)
                thinkingBatchCallback.accept(r.reasoning());
            if (r.content() != null && !r.content().isEmpty() && textTokenCallback != null)
                textTokenCallback.accept(r.content());
            return r;
        }
        usageCallGate.beforeCall(usageContext, config);
        String callId = UUID.randomUUID().toString();
        String body = buildStreamBody(messages, toolsJson, maxTokens, toolChoice, 0.1);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(config.chatCompletionsUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<java.util.stream.Stream<String>> resp =
                httpClient.send(req, HttpResponse.BodyHandlers.ofLines());

        if (resp.statusCode() != 200) {
            String hint = resp.body().limit(5)
                    .collect(java.util.stream.Collectors.joining(""));
            Matcher em = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]+)\"").matcher(hint);
            throw new RuntimeException("LLM API error " + resp.statusCode()
                    + ": " + (em.find() ? em.group(1) : truncate(hint, 200)));
        }

        StringBuilder fullContent   = new StringBuilder();
        StringBuilder fullReasoning = new StringBuilder();
        String[]      finishReason  = { FINISH_STOP };
        boolean[]     thinkingEmitted = { false };

        Map<Integer, String[]> tcAccum = new LinkedHashMap<>();
        Usage[] streamUsage = { null };

        try {
            resp.body().forEach(line -> {
                if (!line.startsWith("data:")) return;
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) return;
                try {
                    JsonNode chunk = JSON_MAPPER.readTree(data);
                    JsonNode usageNode = chunk.path("usage");
                    if (usageNode.isObject()) streamUsage[0] = parseUsage(usageNode);

                    JsonNode choice = chunk.path("choices").path(0);
                    String fr = choice.path("finish_reason").asText("");
                    if (!fr.isEmpty() && !"null".equals(fr)) finishReason[0] = fr;

                    JsonNode delta = choice.path("delta");

                    JsonNode reasoningNode = delta.path("reasoning_content");
                    if (!reasoningNode.isMissingNode() && !reasoningNode.isNull()) {
                        String token = reasoningNode.asText("");
                        if (!token.isEmpty()) fullReasoning.append(token);
                    }

                    JsonNode contentNode = delta.path("content");
                    if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                        String token = contentNode.asText("");
                        if (!token.isEmpty()) {
                            if (!thinkingEmitted[0] && fullReasoning.length() > 0
                                    && thinkingBatchCallback != null) {
                                thinkingBatchCallback.accept(fullReasoning.toString());
                                thinkingEmitted[0] = true;
                            }
                            fullContent.append(token);
                            if (textTokenCallback != null) textTokenCallback.accept(token);
                        }
                    }

                    JsonNode toolCallsNode = delta.path("tool_calls");
                    if (toolCallsNode.isArray()) {
                        for (JsonNode tc : toolCallsNode) {
                            int idx = tc.path("index").asInt(0);
                            String[] acc = tcAccum.computeIfAbsent(idx, k -> new String[]{"", "", ""});
                            String id = tc.path("id").asText("");
                            if (!id.isEmpty()) acc[0] = id;
                            JsonNode fn = tc.path("function");
                            String name = fn.path("name").asText("");
                            if (!name.isEmpty()) acc[1] = name;
                            acc[2] += fn.path("arguments").asText("");
                        }
                    }
                } catch (Exception ignored) {
                    // Keep the historical lenient stream parser behavior.
                }
            });
        } catch (RuntimeException streamFailure) {
            if (streamUsage[0] != null) {
                LLMResponse partial = new LLMResponse(
                        finishReason[0], fullContent.toString(),
                        fullReasoning.length() == 0 ? null : fullReasoning.toString(),
                        List.of(), Map.of(), streamUsage[0]);
                publishUsage(partial, callId, false);
            }
            throw streamFailure;
        }

        if (!thinkingEmitted[0] && fullReasoning.length() > 0
                && thinkingBatchCallback != null) {
            thinkingBatchCallback.accept(fullReasoning.toString());
        }

        if (FINISH_TOOL_CALLS.equals(finishReason[0]) && !tcAccum.isEmpty()) {
            List<ToolCall> calls = new ArrayList<>();
            StringBuilder tcJson = new StringBuilder("[");
            boolean first = true;
            for (String[] acc : tcAccum.values()) {
                if (!first) tcJson.append(",");
                first = false;
                calls.add(new ToolCall(acc[0], acc[1], acc[2]));
                tcJson.append("{\"id\":\"").append(acc[0])
                      .append("\",\"type\":\"function\",\"function\":{\"name\":\"")
                      .append(acc[1]).append("\",\"arguments\":")
                      .append(jsonString(acc[2])).append("}}");
            }
            tcJson.append("]");
            Map<String, Object> assistantMsg = new LinkedHashMap<>();
            assistantMsg.put("role",       "assistant");
            assistantMsg.put("content",    null);
            assistantMsg.put("tool_calls", tcJson.toString());
            return publishUsage(new LLMResponse(
                    FINISH_TOOL_CALLS, null, null, calls, assistantMsg, streamUsage[0]), callId, true);
        }

        String reasoning = fullReasoning.length() > 0 ? fullReasoning.toString() : null;
        return publishUsage(new LLMResponse(finishReason[0], fullContent.toString(), reasoning,
                               List.of(), Map.of(), streamUsage[0]), callId, true);
    }

    public static String buildToolsJson(List<? extends LlmToolSpec> tools) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < tools.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"type\":\"function\",\"function\":")
              .append(tools.get(i).toolDefinitionJson()).append("}");
        }
        return sb.append("]").toString();
    }

    public static String jsonString(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                        .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    /**
     * Serialize an arbitrary JSON-shaped value (String / Number / Boolean / Map /
     * List / null) using the same escaping as {@link #jsonString}. Used for
     * multimodal content-block arrays; reuses the hand-built JSON style of this
     * class so no Jackson checked-exception handling leaks into messageToJson.
     */
    public static String jsonValue(Object v) {
        if (v == null) return "null";
        if (v instanceof String s) return jsonString(s);
        if (v instanceof Number || v instanceof Boolean) return String.valueOf(v);
        if (v instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append(jsonString(String.valueOf(e.getKey())))
                  .append(":").append(jsonValue(e.getValue()));
            }
            return sb.append("}").toString();
        }
        if (v instanceof Iterable<?> it) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : it) {
                if (!first) sb.append(",");
                first = false;
                sb.append(jsonValue(item));
            }
            return sb.append("]").toString();
        }
        return jsonString(String.valueOf(v));
    }

    public static String unescape(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"").replace("\\n", "\n")
                .replace("\\r", "\r").replace("\\t", "\t").replace("\\\\", "\\");
    }

    public static String messagesToJson(List<Map<String, Object>> messages) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(messageToJson(messages.get(i)));
        }
        return sb.append("]").toString();
    }

    public static String messageToJson(Map<String, Object> msg) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"role\":\"").append(msg.get("role")).append("\"");

        Object content = msg.get("content");
        if (content == null) {
            sb.append(",\"content\":null");
        } else if (content instanceof List<?> blocks) {
            // Multimodal / structured content: a List of content-block maps
            // (e.g. [{type:text,...},{type:image_url,...}]) is emitted as a JSON
            // array. String content keeps its exact prior serialization — this
            // branch is inert until a caller supplies List content.
            sb.append(",\"content\":").append(jsonValue(blocks));
        } else {
            sb.append(",\"content\":").append(jsonString(String.valueOf(content)));
        }

        if (msg.containsKey("tool_calls"))
            sb.append(",\"tool_calls\":").append(msg.get("tool_calls"));
        if (msg.containsKey("tool_call_id"))
            sb.append(",\"tool_call_id\":\"").append(msg.get("tool_call_id")).append("\"");
        if (msg.containsKey("name"))
            sb.append(",\"name\":\"").append(msg.get("name")).append("\"");

        return sb.append("}").toString();
    }

    public static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }

    private String buildBody(List<Map<String, Object>> messages,
                             String toolsJson,
                             int maxTokens,
                             String toolChoice,
                             double temperature) {
        return "{\"model\":" + jsonString(config.model())
                + ",\"temperature\":" + temperature
                + ",\"max_tokens\":" + maxTokens
                + ",\"messages\":" + messagesToJson(messages)
                + ",\"tools\":" + toolsJson
                + ",\"tool_choice\":\"" + toolChoice + "\"}";
    }

    private String buildTextOnlyBody(List<Map<String, Object>> messages,
                                     int maxTokens,
                                     double temperature) {
        return "{\"model\":" + jsonString(config.model())
                + ",\"temperature\":" + temperature
                + ",\"max_tokens\":" + maxTokens
                + ",\"messages\":" + messagesToJson(messages) + "}";
    }

    private String buildStreamBody(List<Map<String, Object>> messages,
                                   String toolsJson, int maxTokens, String toolChoice,
                                   double temperature) {
        return "{\"model\":" + jsonString(config.model())
                + ",\"temperature\":" + temperature
                + ",\"max_tokens\":" + maxTokens
                + ",\"stream\":true"
                + ",\"stream_options\":{\"include_usage\":true}"
                + ",\"messages\":" + messagesToJson(messages)
                + ",\"tools\":" + toolsJson
                + ",\"tool_choice\":\"" + toolChoice + "\"}";
    }

    private String send(String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(config.chatCompletionsUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            String errBody = resp.body();
            Matcher em = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]+)\"").matcher(errBody);
            String hint = em.find() ? em.group(1) : errBody;
            throw new RuntimeException("LLM API error " + resp.statusCode() + ": " + hint);
        }
        return resp.body();
    }

    private LLMResponse parseResponse(String responseBody) {
        // Primary: structured JSON parse. This handles arbitrarily long message
        // content without the previous "(?:[^"\\]|\\.)*" regex, which compiles to a
        // recursive matcher and overflows the JVM stack on large bodies (e.g. a long
        // document summary) — killing the worker thread mid-response.
        try {
            JsonNode root = JSON_MAPPER.readTree(responseBody);
            Usage usage = parseUsage(root.path("usage"));
            JsonNode choice = root.path("choices").path(0);
            if (!choice.isMissingNode() && !choice.isNull()) {
                String finishReason = choice.path("finish_reason").asText("");
                if (finishReason.isEmpty() || "null".equals(finishReason)) finishReason = FINISH_STOP;
                if (FINISH_TOOL_CALLS.equals(finishReason)) {
                    return toolCallResponse(responseBody, usage);
                }
                JsonNode message = choice.path("message");
                JsonNode contentNode = message.path("content");
                String content = contentNode.isTextual() ? contentNode.asText() : null;
                JsonNode rcNode = message.path("reasoning_content");
                String reasoning = (rcNode.isTextual() && !rcNode.asText().isEmpty())
                        ? rcNode.asText() : null;
                return new LLMResponse(finishReason, content, reasoning, List.of(), Map.of(), usage);
            }
        } catch (Exception ignore) {
            // Body wasn't valid JSON — fall through to the lenient, non-recursive scan.
        }

        Matcher fm = FINISH_REASON_PAT.matcher(responseBody);
        String finishReason = fm.find() ? fm.group(1) : FINISH_STOP;
        if (FINISH_TOOL_CALLS.equals(finishReason)) {
            return toolCallResponse(responseBody, Usage.unknown(config.provider(), config.model()));
        }
        String content = LlmToolCallParser.firstStringField(responseBody, "content");
        String reasoning = LlmToolCallParser.firstStringField(responseBody, "reasoning_content");
        return new LLMResponse(finishReason,
                content != null ? content : responseBody,
                (reasoning != null && !reasoning.isEmpty()) ? reasoning : null,
                List.of(), Map.of(), Usage.unknown(config.provider(), config.model()));
    }

    private LLMResponse toolCallResponse(String responseBody, Usage usage) {
        String toolCallsJson = LlmToolCallParser.extractToolCallsArray(responseBody);
        List<ToolCall> calls = LlmToolCallParser.parseToolCalls(toolCallsJson).stream()
                .map(tc -> new ToolCall(tc.id(), tc.name(), tc.arguments()))
                .toList();
        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role",       "assistant");
        assistantMsg.put("content",    null);
        assistantMsg.put("tool_calls", toolCallsJson);
        return new LLMResponse(FINISH_TOOL_CALLS, null, null, calls, assistantMsg, usage);
    }

    private Usage parseUsage(JsonNode usageNode) {
        if (usageNode == null || !usageNode.isObject()) {
            return Usage.unknown(config.provider(), config.model());
        }

        Long input = firstLong(usageNode, "prompt_tokens", "input_tokens", "inputTokens");
        Long output = firstLong(usageNode, "completion_tokens", "output_tokens", "outputTokens");
        Long cached = firstLong(usageNode,
                "prompt_cache_hit_tokens", "cached_input_tokens", "cached_tokens", "cachedInputTokens");
        Long uncached = firstLong(usageNode,
                "prompt_cache_miss_tokens", "uncached_input_tokens", "uncachedInputTokens");
        if (cached == null) {
            cached = firstLong(usageNode.path("prompt_tokens_details"),
                    "cached_tokens", "cached_input_tokens");
        }
        if (cached == null) {
            cached = firstLong(usageNode.path("input_tokens_details"),
                    "cached_tokens", "cached_input_tokens");
        }

        // Missing cache details are a valid zero-cache report when the provider did report the
        // total input/output counters. Missing input or output counters remain unknown because
        // the event is not safe for formal billing.
        if (input == null && cached != null && uncached != null) {
            input = cached + uncached;
        }
        if (input == null || output == null) {
            return new Usage(config.provider(), config.model(), null, null, "unknown",
                    input, cached, null, output, Usage.UNKNOWN, rawJson(usageNode));
        }
        long cachedValue = cached == null ? 0L : Math.max(0L, cached);
        long uncachedValue = uncached == null
                ? Math.max(input - cachedValue, 0L) : Math.max(uncached, 0L);
        if (cachedValue + uncachedValue > input) {
            uncachedValue = Math.max(input - cachedValue, 0L);
        }
        return new Usage(config.provider(), config.model(), null, null, "unknown",
                input, cachedValue, uncachedValue, output, Usage.REPORTED, rawJson(usageNode));
    }

    private static Long firstLong(JsonNode node, String... names) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isNumber()) return value.asLong();
            if (value.isTextual()) {
                try {
                    return Long.parseLong(value.asText());
                } catch (NumberFormatException ignored) {
                    // Try the next provider alias.
                }
            }
        }
        return null;
    }

    private static String rawJson(JsonNode node) {
        try {
            return JSON_MAPPER.writeValueAsString(node);
        } catch (Exception ignored) {
            return node == null ? null : node.toString();
        }
    }

    private LLMResponse publishUsage(LLMResponse response, String callId, boolean callSucceeded) {
        Usage base = response.usage() == null
                ? Usage.unknown(config.provider(), config.model()) : response.usage();
        Usage usage = base.withIdentity(callId, usageContext.turnId(), usageContext.callType());
        UsageEvent event = new UsageEvent(
                UUID.randomUUID().toString(),
                usageContext.userId(),
                usageContext.sessionId(),
                usageContext.featureCode(),
                usageContext.billingMode(),
                usageContext.billable(),
                callSucceeded,
                usage,
                java.time.Instant.now());
        try {
            usageObserver.onUsage(event);
        } catch (RuntimeException ignored) {
            // Observer implementations own their durable retry policy. A completed provider
            // response must not be converted into an SSE/model failure.
        }
        return new LLMResponse(response.finishReason(), response.content(), response.reasoning(),
                response.toolCalls(), response.rawAssistantMessage(), usage);
    }

    public record LLMResponse(
            String finishReason,
            String content,
            String reasoning,
            List<ToolCall> toolCalls,
            Map<String, Object> rawAssistantMessage,
            Usage usage) {
        public LLMResponse(String finishReason,
                           String content,
                           String reasoning,
                           List<ToolCall> toolCalls,
                           Map<String, Object> rawAssistantMessage) {
            this(finishReason, content, reasoning, toolCalls, rawAssistantMessage, null);
        }
    }

    public record ToolCall(String id, String name, String arguments) {
        public Map<String, Object> parsedArgs() {
            return new LlmToolCallParser.ToolCallInfo(id, name, arguments).parsedArgs();
        }
    }
}
