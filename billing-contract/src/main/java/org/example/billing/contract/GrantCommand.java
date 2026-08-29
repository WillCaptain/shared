package org.example.billing.contract;

import java.time.Instant;

/** Restricted future funding command. Payment-One never writes Billing tables directly. */
public record GrantCommand(
        String idempotencyKey,
        BillingAccountOwnerType ownerType,
        String ownerId,
        long amountMinor,
        String currency,
        String sourceType,
        String referenceId,
        Instant expiresAt,
        String metadata) {

    public GrantCommand {
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        ownerType = ownerType == null ? BillingAccountOwnerType.USER : ownerType;
        ownerId = required(ownerId, "ownerId");
        if (amountMinor <= 0) throw new IllegalArgumentException("amountMinor must be positive");
        currency = currency == null || currency.isBlank() ? "CREDIT" : currency.strip();
        sourceType = sourceType == null || sourceType.isBlank() ? "external" : sourceType.strip();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.strip();
    }
}
