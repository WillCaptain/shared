package org.twelve.aipp.host;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AlertHostInterfaceSpecTest {
    @Test
    void describesProviderNeutralAlertRuntime() {
        assertThat(AlertHostInterfaceSpec.effect("alerts-provider", "/runtime/alerts.js"))
                .containsEntry("type", "shared.alert.runtime/v1")
                .extracting("payload")
                .asString()
                .contains("alerts-provider", "/runtime/alerts.js");
    }
}
