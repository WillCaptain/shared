package org.example.billing.contract;

public record AdjustmentCommand(
        String idempotencyKey,
        BillingAccountOwnerType ownerType,
        String ownerId,
        long amountDeltaMinor,
        String currency,
        String reason,
        String metadata) {
    public AdjustmentCommand {
        if (idempotencyKey == null || idempotencyKey.isBlank())
            throw new IllegalArgumentException("idempotencyKey is required");
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId is required");
        if (amountDeltaMinor == 0) throw new IllegalArgumentException("amountDeltaMinor must not be zero");
        ownerType = ownerType == null ? BillingAccountOwnerType.USER : ownerType;
        currency = currency == null || currency.isBlank() ? "CREDIT" : currency.strip();
        reason = reason == null || reason.isBlank() ? "adjustment" : reason.strip();
    }
}
