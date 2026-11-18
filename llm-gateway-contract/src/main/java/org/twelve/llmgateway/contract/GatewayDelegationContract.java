package org.twelve.llmgateway.contract;

/** Provider-neutral tool-manifest names for Host-issued LLM gateway delegation. */
public interface GatewayDelegationContract {
    String POLICY_FIELD = "gateway_delegation";
    String MODE_FIELD = "mode";
    String FEATURE_FIELD = "feature";
    String TTL_SECONDS_FIELD = "ttl_seconds";
    /** Optional bounded list of job scopes; omitted preserves the original chat-only job scope. */
    String OPERATIONS_FIELD = "operations";
    String INTERACTIVE_MODE = "interactive";
    String JOB_MODE = "job";
}
