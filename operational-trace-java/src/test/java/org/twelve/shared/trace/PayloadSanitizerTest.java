package org.twelve.shared.trace;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PayloadSanitizerTest {
    @Test
    void redactsSecretKeysRecursively() {
        Map<String, Object> out = PayloadSanitizer.sanitizeMap(Map.of(
                "tool_name", "set_workspace",
                "api_key", "secret-value",
                "nested", Map.of("Authorization", "Bearer xyz", "safe", true)));

        assertEquals("set_workspace", out.get("tool_name"));
        assertFalse(out.containsKey("api_key"));
        assertEquals(Map.of("safe", true), out.get("nested"));
    }

    @Test
    void truncatesLongStrings() {
        Map<String, Object> out = PayloadSanitizer.sanitizeMap(Map.of("note", "x".repeat(600)));
        assertTrue(String.valueOf(out.get("note")).length() < 520);
    }

    @Test
    void preservesNullValuesWithoutMakingTheTraceFail() {
        java.util.LinkedHashMap<String, Object> input = new java.util.LinkedHashMap<>();
        input.put("optional", null);
        assertDoesNotThrow(() -> PayloadSanitizer.sanitizeMap(input));
        assertTrue(PayloadSanitizer.sanitizeMap(input).containsKey("optional"));
    }
}
