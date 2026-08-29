package org.example.billing.client;

import org.example.billing.contract.BillingAuthorizationPort;
import org.example.billing.contract.BillingQueryPort;
import org.example.billing.contract.BillingSettlementQueryPort;
import org.example.billing.contract.UsageEventPort;

/** Aggregate transport port used by Spring wiring; business semantics remain in contract ports. */
public interface BillingClientPort extends BillingAuthorizationPort, UsageEventPort, BillingQueryPort,
        BillingSettlementQueryPort {
}
