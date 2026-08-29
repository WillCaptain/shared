package org.twelve.llmgateway.contract;

import java.util.List;
import java.util.Map;

public record ChatInvocationRequest(
        String idempotencyKey,
        String modelAlias,
        List<Map<String, Object>> messages,
        String toolsJson,
        Integer maxTokens,
        Double temperature,
        String toolChoice,
        Map<String, Object> reasoning) {
    public ChatInvocationRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        reasoning = reasoning == null ? Map.of() : Map.copyOf(reasoning);
        if (messages.isEmpty()) throw new IllegalArgumentException("messages are required");
        if (maxTokens != null && (maxTokens < 1 || maxTokens > 131072)) {
            throw new IllegalArgumentException("maxTokens is out of range");
        }
        if (temperature != null && (!Double.isFinite(temperature) || temperature < 0 || temperature > 2)) {
            throw new IllegalArgumentException("temperature is out of range");
        }
    }

    public static ChatInvocationRequest text(List<Map<String, Object>> messages, int maxTokens) {
        return new ChatInvocationRequest(null, null, messages, null, maxTokens, 0.1, null, Map.of());
    }
}
