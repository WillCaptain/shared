package org.example.billing.contract;

import java.util.Objects;

/**
 * Transport metadata for an internal Billing request. Credentials are opaque strings: this
 * contract deliberately contains no signer, parser or authentication implementation.
 */
public record BillingRequestMetadata(
        String callerService,
        String serviceCredential,
        String billingSubjectAssertion,
        String requestId) {

    public BillingRequestMetadata {
        callerService = required(callerService, "callerService");
        serviceCredential = required(serviceCredential, "serviceCredential");
        billingSubjectAssertion = clean(billingSubjectAssertion);
        requestId = clean(requestId);
    }

    public static BillingRequestMetadata worldOne(String serviceCredential,
                                                  String billingSubjectAssertion,
                                                  String requestId) {
        return new BillingRequestMetadata("world-one", serviceCredential,
                billingSubjectAssertion, requestId);
    }

    private static String required(String value, String name) {
        String normalized = clean(value);
        return Objects.requireNonNull(normalized, name + " is required");
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.strip();
    }
}
