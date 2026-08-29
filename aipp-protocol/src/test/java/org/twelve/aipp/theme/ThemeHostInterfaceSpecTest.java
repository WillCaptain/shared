package org.twelve.aipp.theme;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThemeHostInterfaceSpecTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final ThemeHostInterfaceSpec spec = new ThemeHostInterfaceSpec();

    @Test
    void validatesCurrentAndOwnerSuppliedDefaultFallbackEffects() {
        Map<String, Object> current = spec.effect(payload("user.alice.blue"));
        Map<String, Object> fallback = spec.effect(payload("ones.standard.dark"));
        Map<String, Object> response = Map.of(
                "ok", true,
                "host_effect", current,
                "fallback_effect", fallback);

        assertThatNoException().isThrownBy(
                () -> spec.assertValidBootstrapResponse(JSON.valueToTree(response)));
        assertThat(current.get("type")).isEqualTo(ThemeHostInterfaceSpec.EFFECT_TYPE);
    }

    @Test
    void rejectsMissingFallbackAndInvalidOpaqueDescriptorIdentity() {
        assertThatThrownBy(() -> spec.assertValidBootstrapResponse(JSON.valueToTree(Map.of(
                "ok", true,
                "host_effect", spec.effect(payload("user.alice.blue"))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fallback_effect");

        Map<String, Object> invalid = new LinkedHashMap<>(payload("user.alice.blue"));
        invalid.put("instance_id", "not-an-instance");
        assertThatThrownBy(() -> spec.effect(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("instance_id");
    }

    private static Map<String, Object> payload(String packageId) {
        return Map.of(
                "schema_version", 1,
                "package_id", packageId,
                "version", "1.0.0",
                "instance_id", "0123456789abcdef01234567",
                "tokens", Map.of(),
                "shell", Map.of(),
                "animation", Map.of("program", Map.of(), "fallback", Map.of()),
                "assets", Map.of());
    }
}
