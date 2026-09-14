package org.twelve.aipp.invocation;
import org.junit.jupiter.api.Test;
import org.twelve.aipp.identity.InvocationRequest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InvocationTransportTest {
    private final InvocationRequest request = new InvocationRequest("sample-app", "write", "POST", "/api/tools/write", new byte[0]);
    private final Map<String,Object> protectedTool = Map.of("invocation_identity", "verified-request-v1");
    @Test void legacyToolDoesNotIssueEvidence() {
        var prepared = InvocationTransport.prepare(Map.of(), request, ignored -> { throw new AssertionError("must not issue"); });
        assertTrue(prepared.identityHeaders().isEmpty()); assertSame(request, prepared.request());
    }
    @Test void unsupportedRequirementOrMissingIssuerFailsClosed() {
        assertThrows(SecurityException.class, () -> InvocationTransport.prepare(protectedTool, request, null));
        for (Object value : List.of(false, "unknown", Map.of()))
            assertThrows(SecurityException.class, () -> InvocationTransport.prepare(Map.of("invocation_identity", value), request, r -> "proof"));
        var explicitNull = new HashMap<String,Object>(); explicitNull.put("invocation_identity", null);
        assertThrows(SecurityException.class, () -> InvocationTransport.prepare(explicitNull, request, r -> "proof"));
    }
    @Test void issuerReceivesExactFinalSnapshotAndDiagnosticsAreRedacted() {
        var prepared = InvocationTransport.prepare(protectedTool, request, r -> { assertSame(request, r); return "opaque-proof"; });
        assertEquals("opaque-proof", prepared.identityHeaders().get("X-Aipp-Invocation-Identity"));
        assertEquals("PreparedInvocation[redacted]", prepared.toString());
        assertThrows(UnsupportedOperationException.class, () -> prepared.identityHeaders().clear());
    }
    @Test void invalidHeaderAndIssuerFailuresAreSanitized() {
        for (String value : List.of("", "x\r\nInjected: value", "x".repeat(16385)))
            assertThrows(SecurityException.class, () -> InvocationTransport.prepare(protectedTool, request, r -> value));
        var failure = assertThrows(SecurityException.class, () -> InvocationTransport.prepare(protectedTool, request, r -> { throw new IllegalStateException("secret"); }));
        assertEquals("invocation_identity_unavailable", failure.getMessage()); assertNull(failure.getCause());
    }
}
