package org.twelve.aipp.host;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HostToolClientTest {

    @Test
    void caller_context_is_beside_args() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", "doc");
        args.put("_context", Map.of("userId", "00000000-0000-0000-0000-000000000001"));

        Map<String, Object> body = HostToolClient.requestBody(args);

        assertThat(body.get("args")).isEqualTo(Map.of("path", "doc"));
        assertThat(body.get("_context")).isEqualTo(Map.of("userId", "00000000-0000-0000-0000-000000000001"));
    }
}
