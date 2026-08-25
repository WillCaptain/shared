package org.twelve.shared.llm;

/** Observer boundary for completed provider usage. */
@FunctionalInterface
public interface UsageObserver {

    void onUsage(UsageEvent event);
}
