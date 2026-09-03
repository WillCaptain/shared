package org.twelve.aipp.sting;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StingHostInterfaceSpecTest {

    @Test
    void publishesAppOwnedProjectionTypes() {
        assertThat(StingHostInterfaceSpec.WALL_WIDGET_TYPE).isEqualTo("sting-wall");
        assertThat(StingHostInterfaceSpec.CARD_WIDGET_TYPE).isEqualTo("sting-card");
        assertThat(StingHostInterfaceSpec.COUNTDOWN_WIDGET_TYPE).isEqualTo("sting-countdown-card");
        assertThat(java.util.List.of(
                StingHostInterfaceSpec.LAUNCHER_EFFECT_TYPE,
                StingHostInterfaceSpec.FLASH_EFFECT_TYPE,
                StingHostInterfaceSpec.HELP_EFFECT_TYPE)).containsExactly(
                "shared.sting.launcher/v1", "shared.sting.flash/v1", "shared.sting.help/v1");
        assertThat(StingHostInterfaceSpec.effect(StingHostInterfaceSpec.FLASH_EFFECT_TYPE,
                "sting-one", "/runtime/sting-flash-interface.js").get("type"))
                .isEqualTo("shared.sting.flash/v1");
    }
}
