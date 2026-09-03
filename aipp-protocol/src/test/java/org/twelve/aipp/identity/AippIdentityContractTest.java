package org.twelve.aipp.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AippIdentityContractTest {
    @Test
    void remainsAProviderNeutralInterfaceWithoutImplementationMethods() {
        assertThat(AippIdentityContract.class.isInterface()).isTrue();
        assertThat(AippIdentityContract.class.getDeclaredMethods()).isEmpty();
        assertThat(AippIdentityContract.GET_USER_TOOL_NAME).isEqualTo("get_user");
        assertThat(AippIdentityContract.ACCOUNT_UI_PROVIDER_PATH)
                .isEqualTo("/api/identity/account-ui.js");
        assertThat(AippIdentityContract.class.getName()).doesNotContain("userone", "UserOne");
    }
}
