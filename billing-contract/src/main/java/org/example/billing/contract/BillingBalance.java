package org.example.billing.contract;

import java.time.Instant;

public record BillingBalance(
        String accountId,
        BillingAccountOwnerType ownerType,
        String ownerId,
        long availableMinor,
        int minorUnitsPerCredit,
        String availableCredits,
        String currency,
        String status,
        Instant updatedAt) {

    public BillingBalance {
        CreditUnits.requireSupportedScale(minorUnitsPerCredit);
        availableCredits = availableCredits == null
                ? CreditUnits.format(availableMinor, minorUnitsPerCredit) : availableCredits;
    }
}
