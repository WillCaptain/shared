package org.example.billing.contract;

import java.time.Instant;

/** Decision plus the immutable snapshot fields needed by the Gateway for audit binding. */
public record BillingDecisionResponse(
        String decisionId,
        boolean allowed,
        BillingErrorCode reasonCode,
        String authenticatedSubject,
        String callerService,
        String callId,
        String payer,
        BillingAccountOwnerType accountOwnerType,
        String accountOwnerId,
        FundingMode fundingMode,
        boolean billable,
        String operation,
        String featureCode,
        String provider,
        String model,
        CredentialSource credentialSource,
        String pricingRuleId,
        String pricingRuleVersion,
        String policyVersion,
        Long estimatedExposure,
        Instant issuedAt,
        Instant expiresAt) {

    public boolean canStartAt(Instant at) {
        if (!allowed || expiresAt == null) return false;
        Instant point = at == null ? Instant.now() : at;
        return (issuedAt == null || !point.isBefore(issuedAt)) && point.isBefore(expiresAt);
    }
}
