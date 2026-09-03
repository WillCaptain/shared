package org.twelve.aipp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AippFunctionAuthoritySpecTest {
    @Test
    void exposesProviderNeutralWireNamesOnly() {
        assertThat(AippFunctionAuthoritySpec.REGISTER_FUNCTION_TOOL).isEqualTo("register_function");
        assertThat(AippFunctionAuthoritySpec.CHECK_FUNCTIONS_TOOL).isEqualTo("check_functions");
        assertThat(AippFunctionAuthoritySpec.FIELD_FUNCTION_ID).isEqualTo("function_id");
        assertThat(AippFunctionAuthoritySpec.FIELD_REQUIRES_AUTHORITY)
                .isEqualTo("requires_authority");
    }

    @Test
    void sharedContractHasNoPolicyImplementationOrProviderIdentity() {
        assertThat(AippFunctionAuthoritySpec.class.getDeclaredMethods()).isEmpty();
        assertThat(AippFunctionAuthoritySpec.class.getDeclaredFields())
                .extracting(field -> field.getName().toLowerCase())
                .noneMatch(name -> name.contains("owner") || name.contains("user_one"));
    }
}
