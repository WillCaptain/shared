package org.twelve.shared.llm;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Pool-aware LLM facade: acquire a key per request, invoke {@link LLMCaller}, failover on 401/429.
 */
public final class LlmPoolClient {

    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final LlmPool pool;
    private final int maxAttempts;

    public LlmPoolClient(LlmPool pool) {
        this(pool, DEFAULT_MAX_ATTEMPTS);
    }

    public LlmPoolClient(LlmPool pool, int maxAttempts) {
        this.pool = Objects.requireNonNull(pool, "pool");
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public LlmPool pool() {
        return pool;
    }

    public LLMCaller.LLMResponse call(List<Map<String, Object>> messages,
                                      String toolsJson,
                                      int maxTokens,
                                      String toolChoice) throws Exception {
        return invoke(caller -> caller.call(messages, toolsJson, maxTokens, toolChoice));
    }

    public LLMCaller.LLMResponse callTextOnly(List<Map<String, Object>> messages,
                                              int maxTokens) throws Exception {
        return invoke(caller -> caller.callTextOnly(messages, maxTokens));
    }

    public LLMCaller.LLMResponse callStream(List<Map<String, Object>> messages,
                                            String toolsJson,
                                            int maxTokens,
                                            String toolChoice,
                                            Consumer<String> textTokenCallback,
                                            Consumer<String> thinkingBatchCallback) throws Exception {
        return invoke(caller -> caller.callStream(
                messages, toolsJson, maxTokens, toolChoice,
                textTokenCallback, thinkingBatchCallback));
    }

    private LLMCaller.LLMResponse invoke(CallerOp op) throws Exception {
        if (pool.isEmpty()) {
            throw new IllegalStateException("LLM key pool is empty");
        }
        int attempts = Math.min(maxAttempts, pool.size());
        Exception last = null;
        for (int i = 0; i < attempts; i++) {
            Optional<String> keyOpt = pool.acquire();
            if (keyOpt.isEmpty()) {
                throw new IllegalStateException("LLM key pool has no usable key");
            }
            String key = keyOpt.get();
            LLMConfig cfg = pool.baseConfig().withApiKey(key);
            try {
                LLMCaller.LLMResponse resp = op.run(new LLMCaller(cfg));
                pool.reportSuccess(key);
                return resp;
            } catch (Exception e) {
                last = e;
                int status = LlmPool.parseHttpStatus(e).orElse(0);
                pool.reportFailure(key, status, e.getMessage());
                if (!LlmPool.isRetryableStatus(status) || i == attempts - 1) {
                    throw e;
                }
            }
        }
        throw last != null ? last : new IllegalStateException("LLM pool request failed");
    }

    @FunctionalInterface
    private interface CallerOp {
        LLMCaller.LLMResponse run(LLMCaller caller) throws Exception;
    }
}
