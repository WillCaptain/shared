package org.example.billing.contract;

/** Minimal, non-sensitive settlement projection; no raw Usage or user content crosses this API. */
public record BillingUsageSettlement(
        String eventId,
        String status,
        Long creditAmountMinor,
        String operation) {
}
