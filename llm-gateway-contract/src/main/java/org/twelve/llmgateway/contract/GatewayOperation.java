package org.twelve.llmgateway.contract;

public enum GatewayOperation {
    CHAT("chat"), EMBEDDING("embedding");

    private final String wireValue;
    GatewayOperation(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
