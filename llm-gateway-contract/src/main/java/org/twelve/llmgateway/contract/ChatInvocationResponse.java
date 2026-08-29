package org.twelve.llmgateway.contract;

import java.util.List;
import java.util.Map;

public record ChatInvocationResponse(
        String invocationId, String finishReason, String content, String reasoning,
        List<ToolCall> toolCalls, Map<String, Object> rawAssistantMessage, GatewayUsage usage) {
    public ChatInvocationResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        rawAssistantMessage = rawAssistantMessage == null ? Map.of() : Map.copyOf(rawAssistantMessage);
    }

    public record ToolCall(String id, String name, String arguments) {}
}
