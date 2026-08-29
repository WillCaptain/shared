package org.twelve.llmgateway.contract;

/** Stable transport-neutral error envelope. */
public record GatewayError(GatewayErrorCode code, String message, String invocationId, boolean retryable) {}
