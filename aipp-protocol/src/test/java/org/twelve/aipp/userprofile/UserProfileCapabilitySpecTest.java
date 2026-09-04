package org.twelve.aipp.userprofile;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserProfileCapabilitySpecTest {

    @Test
    void effectCarriesProviderNeutralOperations() {
        Map<String, Object> effect = UserProfileCapabilitySpec.effect(
                "user-one", "/runtime/user-profile-interface.js");
        assertThat(effect.get("type")).isEqualTo(UserProfileCapabilitySpec.INTERFACE_TYPE);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) effect.get("payload");
        assertThat(payload.get("operations")).asList().contains(
                UserProfileCapabilitySpec.PROFILE_VIEW_TOOL,
                UserProfileCapabilitySpec.FIND_USER_TOOL,
                UserProfileCapabilitySpec.GET_PRINCIPAL_TOOL);
    }

    @Test
    void publicProfileRequiresIdAndName() {
        UserProfileCapabilitySpec.assertPublicProfile(Map.of("id", "u1", "name", "Ada"));
        assertThatThrownBy(() -> UserProfileCapabilitySpec.assertPublicProfile(Map.of("id", "u1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
