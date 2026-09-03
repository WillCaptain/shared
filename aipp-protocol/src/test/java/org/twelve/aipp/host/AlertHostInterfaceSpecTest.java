package org.twelve.aipp.host;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AlertHostInterfaceSpecTest {
    @Test
    void describesProviderNeutralAlertRuntime() {
        assertThat(AlertHostInterfaceSpec.EFFECT_TYPE).isEqualTo("shared.alert.runtime/v1");
    }
}
