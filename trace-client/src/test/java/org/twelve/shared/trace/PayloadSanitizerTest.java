package org.twelve.shared.trace;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadSanitizerTest {

    @Test
    void redactsSecretKeys() {
        Map<String, Object> out = PayloadSanitizer.sanitizeMap(Map.of(
                "tool_name", "set_workspace",
                "api_key", "secret-value",
                "Authorization", "Bearer xyz"));
        assertThat(out).containsKey("tool_name");
        assertThat(out).doesNotContainKey("api_key");
        assertThat(out).doesNotContainKey("Authorization");
    }

    @Test
    void truncatesLongStrings() {
        String longVal = "x".repeat(600);
        Map<String, Object> out = PayloadSanitizer.sanitizeMap(Map.of("note", longVal));
        assertThat(String.valueOf(out.get("note")).length()).isLessThan(520);
    }
}
