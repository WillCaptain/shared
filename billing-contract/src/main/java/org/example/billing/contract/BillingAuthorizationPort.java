package org.example.billing.contract;

/** Application port used by a metered caller; the HTTP implementation lives in billing-one-http-adapter. */
public interface BillingAuthorizationPort {
    BillingDecisionResponse decide(BillingDecisionRequest request, BillingRequestMetadata metadata);
}
