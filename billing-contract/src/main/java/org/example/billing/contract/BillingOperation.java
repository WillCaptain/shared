package org.example.billing.contract;

public enum BillingOperation {
    CHAT,
    EMBEDDING,
    IMAGE,
    VIDEO,
    OTHER;

    public String wireValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
