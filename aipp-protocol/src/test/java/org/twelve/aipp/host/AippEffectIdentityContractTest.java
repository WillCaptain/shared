package org.twelve.aipp.host;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AippEffectIdentityContractTest {

    @Test
    void pathIsProtocolReservedUnderToolsName() {
        assertThat(AippEffectIdentityContract.path("example_write"))
                .isEqualTo("/api/tools/example_write/effect-identity");
    }

    @Test
    void pathRejectsBlankAndSegmentInjection() {
        assertThatThrownBy(() -> AippEffectIdentityContract.path(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AippEffectIdentityContract.path("a/b"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void errorClassIsGenericNotProviderWriteVocabulary() {
        assertThat(AippEffectIdentityContract.FIELD).isEqualTo("effect_identity");
        assertThat(AippEffectIdentityContract.ERROR_CLASS).isEqualTo("effect_already_completed");
        assertThat(AippEffectIdentityContract.ERROR_CLASS)
                .doesNotContain("write")
                .doesNotContain("shell")
                .doesNotContain("desktop");
    }
}
