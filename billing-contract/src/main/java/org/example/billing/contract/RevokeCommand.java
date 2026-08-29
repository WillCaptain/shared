package org.example.billing.contract;

/** Compensating command; historical Grant/Ledger rows are never mutated. */
public record RevokeCommand(
        String idempotencyKey,
        BillingAccountOwnerType ownerType,
        String ownerId,
        long amountMinor,
        String currency,
        String referenceId,
        String reason,
        String metadata) {

    public RevokeCommand {
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        ownerType = ownerType == null ? BillingAccountOwnerType.USER : ownerType;
        ownerId = required(ownerId, "ownerId");
        if (amountMinor <= 0) throw new IllegalArgumentException("amountMinor must be positive");
        currency = currency == null || currency.isBlank() ? "CREDIT" : currency.strip();
        reason = required(reason, "reason");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.strip();
    }
}
