package org.twelve.aipp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AippFunctionAuthoritySpecTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final AippFunctionAuthoritySpec spec = new AippFunctionAuthoritySpec();

    @Test
    void acceptsCanonicalFunctionId() {
        assertThatCode(() -> spec.assertValidFunctionId("world::world_list_view"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsBareToolName() {
        assertThatThrownBy(() -> spec.assertValidFunctionId("world_list_view"))
                .hasMessageContaining("function_id");
    }

    @Test
    void acceptsRegisterResponse() throws Exception {
        var node = JSON.readTree("""
                {"ok":true,"function":{"function_id":"world::world_list_view",
                "app_id":"world","name":"world_list_view","kind":"tool","gates_app":true}}
                """);
        assertThatCode(() -> spec.assertValidRegisterFunctionResponse(node)).doesNotThrowAnyException();
    }

    @Test
    void requiresUserOneToOwnRegisterFunction() throws Exception {
        var node = JSON.readTree("""
                {"app":"user-one","tools":[{"name":"register_function"}]}
                """);
        assertThatCode(() -> spec.assertUserOneOwnsRegisterFunction(node)).doesNotThrowAnyException();
    }
}
