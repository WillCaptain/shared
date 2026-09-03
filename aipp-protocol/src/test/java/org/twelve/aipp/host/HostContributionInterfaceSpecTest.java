package org.twelve.aipp.host;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HostContributionInterfaceSpecTest {
    @Test
    void exposesOnlyProviderNeutralInterfaceNames() {
        assertThat(java.util.List.of(
                HostContributionInterfaceSpec.TASK_ADORNMENT_TYPE,
                HostContributionInterfaceSpec.FLASH_RENDERER_TYPE,
                HostContributionInterfaceSpec.HELP_CONTRIBUTION_TYPE))
                .containsExactly(
                        "shared.task-adornment.runtime/v1",
                        "shared.flash-renderer.runtime/v1",
                        "shared.help-contribution.runtime/v1")
                .allMatch(type -> !type.contains("sting"));
        assertThat(HostContributionInterfaceSpec.class.getDeclaredMethods()).isEmpty();
        assertThat(AlertHostInterfaceSpec.class.getDeclaredMethods()).isEmpty();
    }
}
