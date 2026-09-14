package org.twelve.aipp.invocation;

import org.twelve.aipp.identity.InvocationRequest;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import static org.twelve.aipp.identity.AippInvocationIdentityContract.*;

/** Generic pre-dispatch preparation; the Host still owns HTTP transport and function gating. */
public final class InvocationTransport {
    private InvocationTransport() {}

    public record Prepared(InvocationRequest request, Map<String, String> identityHeaders) {
        public Prepared { identityHeaders = Map.copyOf(identityHeaders); }
        @Override public String toString() { return "PreparedInvocation[redacted]"; }
    }

    /**
     * Call after identity/function checks and all body enrichment. The manifest must come from
     * the trusted discovered registry, not invocation arguments. Issuer captures verified identity.
     * Send only the returned snapshot; strip caller-supplied identity evidence before adding headers.
     */
    public static Prepared prepare(Map<String, ?> registeredTool, InvocationRequest finalRequest,
                                   Function<InvocationRequest, String> trustedIssuer) {
        Objects.requireNonNull(registeredTool); Objects.requireNonNull(finalRequest);
        if (!registeredTool.containsKey(REQUIREMENT_FIELD)) return new Prepared(finalRequest, Map.of());
        if (!VERIFIED_REQUEST_V1.equals(registeredTool.get(REQUIREMENT_FIELD)) || trustedIssuer == null)
            throw new SecurityException(IDENTITY_UNAVAILABLE);
        try {
            String evidence = trustedIssuer.apply(finalRequest);
            if (evidence == null || evidence.isBlank() || evidence.length() > 16384
                    || !evidence.chars().allMatch(c -> c > 0x20 && c < 0x7f))
                throw new SecurityException(IDENTITY_UNAVAILABLE);
            return new Prepared(finalRequest, Map.of(EVIDENCE_HEADER, evidence));
        } catch (RuntimeException unavailable) { throw new SecurityException(IDENTITY_UNAVAILABLE); }
    }
}
