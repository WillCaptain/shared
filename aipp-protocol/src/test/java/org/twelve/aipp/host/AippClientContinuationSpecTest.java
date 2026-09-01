package org.twelve.aipp.host;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AippClientContinuationSpecTest {
    @Test
    void buildsOpaqueClientRequestAndOwnerCallback() {
        Map<String, Object> request = AippClientContinuationSpec.request(
                "device_write", Map.of("value", 42),
                "device_write_result", Map.of("resource_id", "r1"));

        assertThat(request).containsEntry("type", AippClientContinuationSpec.TYPE)
                .containsEntry("schema_version", 1)
                .containsEntry("tool", "device_write")
                .containsEntry("callback_tool", "device_write_result");
        assertThat((Map<String, Object>) request.get("args")).containsEntry("value", 42);
        assertThat((Map<String, Object>) request.get("callback_args"))
                .containsEntry("resource_id", "r1");
    }
}
