package org.example.billing.client;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/** Immutable client configuration. Credentials are supplied by the caller and never minted here. */
public record BillingClientConfiguration(
        URI baseUri,
        Duration connectTimeout,
        Duration requestTimeout,
        String serviceCredential) {

    public BillingClientConfiguration {
        baseUri = Objects.requireNonNull(baseUri, "baseUri");
        connectTimeout = sane(connectTimeout, "connectTimeout");
        requestTimeout = sane(requestTimeout, "requestTimeout");
        serviceCredential = required(serviceCredential, "serviceCredential");
    }

    public static BillingClientConfiguration of(String baseUri, String serviceCredential) {
        return new BillingClientConfiguration(URI.create(required(baseUri, "baseUri")),
                Duration.ofSeconds(2), Duration.ofSeconds(5), serviceCredential);
    }

    public URI endpoint(String path) {
        String base = baseUri.toString();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return URI.create(base + (path.startsWith("/") ? path : "/" + path));
    }

    private static Duration sane(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative())
            throw new IllegalArgumentException(name + " must be positive");
        return duration;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.strip();
    }
}
