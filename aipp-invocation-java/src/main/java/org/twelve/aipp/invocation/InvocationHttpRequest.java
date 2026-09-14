package org.twelve.aipp.invocation;

import org.twelve.aipp.identity.InvocationRequest;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import static org.twelve.aipp.identity.AippInvocationIdentityContract.IDENTITY_UNAVAILABLE;

/** Builds an immutable JSON request from the exact signed snapshot; performs no network I/O. */
public final class InvocationHttpRequest {
    private InvocationHttpRequest() {}

    /**
     * origin is trusted routing configuration, with no base path, query or credentials.
     * The caller must enforce identity/function gates first and send with redirects disabled.
     * No inbound headers are accepted: login bearer and caller evidence cannot be copied here.
     */
    public static HttpRequest prepare(URI origin, Map<String, ?> registeredTool,
                                      InvocationRequest snapshot, Duration timeout,
                                      Function<InvocationRequest, String> trustedIssuer) {
        try {
            if (origin == null || origin.getHost() == null || origin.getUserInfo() != null
                    || origin.getRawQuery() != null || origin.getRawFragment() != null
                    || !(origin.getRawPath().isEmpty() || origin.getRawPath().equals("/"))
                    || !("https".equals(origin.getScheme()) || "http".equals(origin.getScheme()))
                    || timeout == null || timeout.isZero() || timeout.isNegative())
                throw new IllegalArgumentException();
            // Do not resolve or normalize the target: either could change signed path/query bytes.
            URI destination = URI.create(origin.getScheme() + "://" + origin.getRawAuthority()
                    + snapshot.requestTarget());
            var prepared = InvocationTransport.prepare(registeredTool, snapshot, trustedIssuer);
            var builder = HttpRequest.newBuilder(destination).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .method(prepared.request().method(),
                            HttpRequest.BodyPublishers.ofByteArray(prepared.request().body()));
            prepared.identityHeaders().forEach(builder::header);
            return builder.build();
        } catch (RuntimeException invalid) {
            throw new SecurityException(IDENTITY_UNAVAILABLE);
        }
    }
}
