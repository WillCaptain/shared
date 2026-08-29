package org.example.billing.contract;

/** Server-resolved provider credential origin, never a credential value. */
public enum CredentialSource {
    USER_CONFIG,
    INSTANCE_CONFIG,
    BYOK,
    PLATFORM_CONFIG,
    UNKNOWN
}
