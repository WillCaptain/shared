package org.example.billing.contract;

/** Application port used by a metered caller; the HTTP implementation lives in billing-java-sdk. */
public interface BillingAuthorizationPort {
    BillingDecisionResponse decide(BillingDecisionRequest request, BillingRequestMetadata metadata);

    /** Releases a hold when the caller will not contact the provider. Settlement owns holds that already have usage. */
    default void releaseReservation(String decisionId, BillingRequestMetadata metadata) {}
}
