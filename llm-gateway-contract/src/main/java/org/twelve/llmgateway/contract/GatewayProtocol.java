package org.twelve.llmgateway.contract;

/** Stable wire-level names shared by client and server implementations. */
public final class GatewayProtocol {
    public static final String SERVICE_HEADER = "X-Ones-Service";
    public static final String SERVICE_CREDENTIAL_HEADER = "X-Ones-Service-Credential";
    public static final String DELEGATION_HEADER = "X-Ones-Gateway-Delegation";
    public static final String DELEGATION_JOB_HEADER = "X-Ones-Gateway-Job";
    private GatewayProtocol() {}
}
