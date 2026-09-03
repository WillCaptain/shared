package org.twelve.aipp.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AippExternalSessionSpecTest {
    @Test
    void isProviderNeutralConstantsOnly() {
        assertThat(AippExternalSessionSpec.TYPE).isEqualTo("external_collaboration");
        assertThat(AippExternalSessionSpec.API_PREFIX).isEqualTo("/api/collaboration");
        assertThat(AippExternalSessionSpec.class.getDeclaredMethods()).isEmpty();
        assertThat(AippExternalSessionSpec.class.getDeclaredFields())
                .allMatch(field -> !field.getName().toLowerCase().contains("chat"));
    }
}
