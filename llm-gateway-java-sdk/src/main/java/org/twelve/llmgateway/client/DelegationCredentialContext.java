package org.twelve.llmgateway.client;

import java.util.Objects;
import java.util.function.Supplier;

/** Explicit propagation hook for background jobs created with a job-scoped delegation. */
public final class DelegationCredentialContext {
    private static final ThreadLocal<Value> CURRENT = new ThreadLocal<>();
    private DelegationCredentialContext() {}
    public static String current() { return CURRENT.get() == null ? null : CURRENT.get().credential(); }
    public static String currentJobId() { return CURRENT.get() == null ? null : CURRENT.get().jobId(); }
    public static Scope bind(String opaqueCredential) {
        return bind(opaqueCredential, null);
    }
    public static Scope bind(String opaqueCredential, String jobId) {
        Value previous = CURRENT.get(); CURRENT.set(new Value(required(opaqueCredential), clean(jobId)));
        return new Scope(previous);
    }
    public static <T> T callWith(String opaqueCredential, Supplier<T> action) {
        Value previous = CURRENT.get(); CURRENT.set(new Value(required(opaqueCredential), null));
        try { return Objects.requireNonNull(action).get(); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
    public static void runWith(String opaqueCredential, Runnable action) {
        callWith(opaqueCredential, () -> { action.run(); return null; });
    }
    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("delegation credential is blank");
        return value.strip();
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private record Value(String credential, String jobId) {}
    public static final class Scope implements AutoCloseable {
        private final Value previous; private boolean closed;
        private Scope(Value previous) { this.previous = previous; }
        @Override public void close() {
            if (closed) return; closed = true;
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }
}
