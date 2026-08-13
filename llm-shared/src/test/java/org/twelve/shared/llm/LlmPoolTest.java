package org.twelve.shared.llm;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LlmPoolTest {

    @Test
    void roundRobinAcquire_andCrud() {
        LlmPool pool = LlmPool.of(
                LLMConfig.builder().baseUrl("https://api.deepseek.com/v1").model("deepseek-v4-flash").build(),
                List.of("sk-a", "sk-b", "sk-c"));

        assertThat(pool.size()).isEqualTo(3);
        Set<String> got = new LinkedHashSet<>();
        got.add(pool.acquire().orElseThrow());
        got.add(pool.acquire().orElseThrow());
        got.add(pool.acquire().orElseThrow());
        assertThat(got).containsExactlyInAnyOrder("sk-a", "sk-b", "sk-c");

        pool.removeKey("sk-b");
        assertThat(pool.snapshotKeys()).containsExactly("sk-a", "sk-c");
        pool.addKeys(List.of("sk-d"));
        assertThat(pool.size()).isEqualTo(3);
        assertThat(pool.maskedKeys()).allMatch(s -> s.contains("****"));
    }

    @Test
    void cooldownSkipsFailedKeyWhenOthersAvailable() {
        LlmPool pool = LlmPool.of(
                LLMConfig.builder().baseUrl("https://x").model("m").build(),
                List.of("sk-good", "sk-bad"),
                3600);

        pool.reportFailure("sk-bad", 401, "auth");
        Optional<String> a = pool.acquire();
        Optional<String> b = pool.acquire();
        assertThat(a).contains("sk-good");
        assertThat(b).contains("sk-good");
    }

    @Test
    void parseHttpStatus_fromCallerMessage() {
        assertThat(LlmPool.parseHttpStatus(
                new RuntimeException("LLM API error 401: Authentication Fails")))
                .contains(401);
        assertThat(LlmPool.isRetryableStatus(429)).isTrue();
        assertThat(LlmPool.isRetryableStatus(500)).isFalse();
    }
}
