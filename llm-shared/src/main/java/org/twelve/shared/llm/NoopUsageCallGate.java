package org.twelve.shared.llm;

/** Default gate preserving the historical LLMCaller behavior. */
public final class NoopUsageCallGate implements UsageCallGate {
    @Override
    public void beforeCall(UsageCallContext context, LLMConfig config) {
        // Intentionally empty.
    }
}
