package org.twelve.aipp.identity;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable snapshot of the final app-bound HTTP request, not identity evidence.
 * Callers must finish argument enrichment before constructing this value.
 * It does not authenticate a caller, verify a signature, or claim a replay ID.
 */
public record InvocationRequest(String audience, String operation, String method,
                                String requestTarget, byte[] body) {
    public InvocationRequest {
        if (audience == null || !audience.matches("[a-z][a-z0-9-]{0,127}"))
            throw new IllegalArgumentException("Invalid invocation audience");
        if (operation == null || !operation.matches("[a-z][a-z0-9_]{0,127}"))
            throw new IllegalArgumentException("Invalid invocation operation");
        if (method == null || !Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS").contains(method))
            throw new IllegalArgumentException("Invocation method must be an exact uppercase HTTP method");
        if (requestTarget == null || !requestTarget.startsWith("/") || requestTarget.startsWith("//"))
            throw new IllegalArgumentException("Invocation target must be app-local origin form");
        URI target;
        try { target = URI.create(requestTarget); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid invocation target"); }
        if (target.isAbsolute() || target.getRawAuthority() != null || target.getRawFragment() != null)
            throw new IllegalArgumentException("Invocation target cannot contain origin or fragment");
        String rawPath = target.getRawPath().toLowerCase(Locale.ROOT);
        if (rawPath.contains("%2f") || rawPath.contains("%5c") || rawPath.contains("%25"))
            throw new IllegalArgumentException("Ambiguous encoded invocation path");
        for (String segment : target.getPath().split("/", -1)) {
            if (segment.equals(".") || segment.equals(".."))
                throw new IllegalArgumentException("Invocation target cannot contain dot segments");
        }
        if (!requestTarget.chars().allMatch(c -> c >= 0x21 && c <= 0x7e))
            throw new IllegalArgumentException("Invocation target must use ASCII percent encoding");
        body = Objects.requireNonNull(body, "Explicit body bytes required; use empty array for no body").clone();
    }

    @Override public byte[] body() { return body.clone(); }

    /** Lowercase hexadecimal SHA-256 of exact forwarded bytes, without JSON normalization. */
    public String bodySha256() {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }

    /** Request targets and bodies can contain secrets; do not print them in diagnostics. */
    @Override public String toString() { return "InvocationRequest[redacted]"; }
}
