package org.twelve.shared.llm;

/**
 * Optional preflight hook for a model call.
 *
 * <p>The shared module knows only that a host may reject a call before the provider request. It
 * does not reserve, freeze, or mutate any resource.
 */
@FunctionalInterface
public interface UsageCallGate {

    void beforeCall(UsageCallContext context, LLMConfig config);
}
