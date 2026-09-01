package org.twelve.aipp.host;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HostNotificationSpecTest {

    @Test
    void exposesGenericHostNotificationContract() {
        assertThat(HostNotificationSpec.PUBLISH_PATH).startsWith("/api/host/integrations/notifications/");
        assertThat(HostNotificationSpec.ACT_PATH).startsWith("/api/host/integrations/notifications/");
        assertThat(HostNotificationSpec.DISMISS_OCCURRENCE_PATH).startsWith("/api/host/integrations/notifications/");
        assertThat(HostNotificationSpec.CONTRACT_VERSION).isEqualTo("v1");
        assertThat(HostNotificationSpec.groupKey("sting", "board:meeting:m1"))
                .isEqualTo("sting:board:meeting:m1");
    }
}
