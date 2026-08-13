package org.twelve.shared.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LLMConfigEmbeddingsUrlTest {

    @Test
    void normalizesOpenAiCompatibleBases() {
        assertThat(LLMConfig.embeddingsUrl("https://api.openai.com/v1"))
                .isEqualTo("https://api.openai.com/v1/embeddings");
        assertThat(LLMConfig.embeddingsUrl("https://api.openai.com/v1/"))
                .isEqualTo("https://api.openai.com/v1/embeddings");
        assertThat(LLMConfig.embeddingsUrl("https://api.openai.com"))
                .isEqualTo("https://api.openai.com/v1/embeddings");
        assertThat(LLMConfig.embeddingsUrl("https://api.openai.com/v1/embeddings"))
                .isEqualTo("https://api.openai.com/v1/embeddings");
    }

    @Test
    void rejectsBlankBase() {
        assertThatThrownBy(() -> LLMConfig.embeddingsUrl("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultDeepseekModelIsV4Flash() {
        assertThat(LLMConfig.of("sk-test").model()).isEqualTo("deepseek-v4-flash");
    }
}
