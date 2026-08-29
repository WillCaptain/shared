package org.twelve.llmgateway.contract;

/** Untrusted correlation and intent metadata. It contains no identity or billing authority. */
public record GatewayRequestMetadata(String sessionId, String turnId, String featureCode, String callType,
                                     String jobId) {
    public GatewayRequestMetadata {
        sessionId = clean(sessionId);
        turnId = clean(turnId);
        featureCode = clean(featureCode);
        callType = clean(callType) == null ? "unknown" : clean(callType);
        jobId = clean(jobId);
    }
    public GatewayRequestMetadata(String sessionId, String turnId, String featureCode, String callType) {
        this(sessionId, turnId, featureCode, callType, null);
    }
    public GatewayRequestMetadata withCallType(String value) {
        return new GatewayRequestMetadata(sessionId, turnId, featureCode, value, jobId);
    }
    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
