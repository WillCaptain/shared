package org.example.billing.contract;

/** Application port for durable-at-least-once Usage delivery. */
public interface UsageEventPort {
    void publish(BillingUsageEvent event, BillingRequestMetadata metadata);
}
