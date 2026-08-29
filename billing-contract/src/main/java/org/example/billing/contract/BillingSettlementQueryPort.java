package org.example.billing.contract;

/** Read-only settlement projection used by World-One's Agent Loop observation reconciler. */
public interface BillingSettlementQueryPort {
    BillingUsageSettlement settlement(BillingRequestMetadata metadata, String eventId);
}
