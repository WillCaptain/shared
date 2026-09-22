package org.twelve.aipp.host;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnnaRuntimeHostInterfaceSpecTest {

    @Test
    void describesProviderNeutralAnnaRuntimeEmbedding() {
        assertThat(AnnaRuntimeHostInterfaceSpec.INTERFACE_TYPE)
                .isEqualTo("shared.anna.runtime/v1");
        assertThat(AnnaRuntimeHostInterfaceSpec.OP_MOUNT).isEqualTo("mount");
        assertThat(AnnaRuntimeHostInterfaceSpec.OP_UPDATE).isEqualTo("update");
        assertThat(AnnaRuntimeHostInterfaceSpec.OP_UNMOUNT).isEqualTo("unmount");
        assertThat(AnnaRuntimeHostInterfaceSpec.OP_COMMAND).isEqualTo("command");
        assertThat(AnnaRuntimeHostInterfaceSpec.COMMAND_FIT).isEqualTo("fit");
        assertThat(AnnaRuntimeHostInterfaceSpec.COMMAND_ZOOM_IN).isEqualTo("zoom-in");
        assertThat(AnnaRuntimeHostInterfaceSpec.COMMAND_ZOOM_OUT).isEqualTo("zoom-out");
    }
}
