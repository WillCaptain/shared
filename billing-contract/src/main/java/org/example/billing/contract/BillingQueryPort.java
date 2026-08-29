package org.example.billing.contract;

import java.util.List;
import java.util.Map;

/** User-facing Credit compatibility queries; the implementation is a thin Billing client. */
public interface BillingQueryPort {
    BillingBalance balance(BillingRequestMetadata metadata, String subjectUserId);
    List<BillingTransaction> transactions(BillingRequestMetadata metadata, String subjectUserId, int limit);
    List<Map<String, Object>> expiring(BillingRequestMetadata metadata, String subjectUserId, int days);
}
