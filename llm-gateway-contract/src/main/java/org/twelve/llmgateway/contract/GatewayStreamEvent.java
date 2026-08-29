package org.twelve.llmgateway.contract;

public record GatewayStreamEvent(String type, String invocationId, String data, GatewayUsage usage) {
    public static GatewayStreamEvent started(String invocationId) {
        return new GatewayStreamEvent("invocation", invocationId, null, null);
    }
}
