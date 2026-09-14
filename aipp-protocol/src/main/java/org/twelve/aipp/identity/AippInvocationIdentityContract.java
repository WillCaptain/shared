package org.twelve.aipp.identity;

/**
 * Provider-neutral names for independently verified invocation identity.
 * Contract groundwork only: declaring these names does not enable authentication.
 * Issuance, trust provisioning, verification and durable replay claims live in adapters.
 * No application permission or domain behavior belongs in this interface.
 */
public interface AippInvocationIdentityContract {
    String REQUIREMENT_FIELD = "invocation_identity";
    String VERIFIED_REQUEST_V1 = "verified-request-v1";
    String EVIDENCE_HEADER = "X-Aipp-Invocation-Identity";

    String VERSION = "version";
    String ISSUER = "issuer";
    String SUBJECT = "subject";
    String ORGANIZATION = "organization";
    String AUDIENCE = "audience";
    String INVOCATION_ID = "invocation_id";
    String ISSUED_AT = "issued_at";
    String EXPIRES_AT = "expires_at";
    String OPERATION = "operation";
    String METHOD = "method";
    String REQUEST_TARGET = "request_target";
    String BODY_SHA256 = "body_sha256";

    String IDENTITY_UNAVAILABLE = "invocation_identity_unavailable";
    String IDENTITY_INVALID = "invocation_identity_invalid";
    String IDENTITY_REPLAYED = "invocation_identity_replayed";
}
