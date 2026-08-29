package org.example.billing.contract;

import java.time.Instant;

public record BillingTransaction(
        String id,
        String accountId,
        String type,
        long amountDeltaMinor,
        long balanceAfterMinor,
        int minorUnitsPerCredit,
        String amountDeltaCredits,
        String balanceAfterCredits,
        String featureCode,
        String referenceType,
        String referenceId,
        String idempotencyKey,
        String pricingRuleVersion,
        String metadata,
        Instant createdAt) {

    public BillingTransaction {
        CreditUnits.requireSupportedScale(minorUnitsPerCredit);
        amountDeltaCredits = amountDeltaCredits == null
                ? CreditUnits.format(amountDeltaMinor, minorUnitsPerCredit) : amountDeltaCredits;
        balanceAfterCredits = balanceAfterCredits == null
                ? CreditUnits.format(balanceAfterMinor, minorUnitsPerCredit) : balanceAfterCredits;
    }
}
