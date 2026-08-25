package org.twelve.shared.llm;

/** Default observer. It keeps shared callers and existing applications behaviorally unchanged. */
public final class NoopUsageObserver implements UsageObserver {
    @Override
    public void onUsage(UsageEvent event) {
        // Intentionally empty.
    }
}
