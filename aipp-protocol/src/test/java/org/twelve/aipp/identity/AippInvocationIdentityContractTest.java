package org.twelve.aipp.identity;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import static org.assertj.core.api.Assertions.assertThat;

class AippInvocationIdentityContractTest {
    @Test void containsWireNamesOnlyWithoutApplicationDependencies() throws Exception {
        Class<?> contract = AippInvocationIdentityContract.class;
        assertThat(contract.isInterface()).isTrue();
        assertThat(contract.getDeclaredMethods()).isEmpty();
        var values = new HashSet<String>();
        for (var field : contract.getDeclaredFields()) {
            assertThat(field.getType()).isEqualTo(String.class);
            assertThat(Modifier.isStatic(field.getModifiers())).isTrue();
            assertThat(Modifier.isFinal(field.getModifiers())).isTrue();
            String value = (String) field.get(null);
            assertThat(value).doesNotContain("entitir", "world-one", "recovery", "ADMIN");
            assertThat(values.add(value)).isTrue();
        }
    }

    @Test void pinsRequirementEvidenceAndBindingNames() {
        assertThat(AippInvocationIdentityContract.REQUIREMENT_FIELD).isEqualTo("invocation_identity");
        assertThat(AippInvocationIdentityContract.VERIFIED_REQUEST_V1).isEqualTo("verified-request-v1");
        assertThat(AippInvocationIdentityContract.EVIDENCE_HEADER).isEqualTo("X-Aipp-Invocation-Identity");
        assertThat(AippInvocationIdentityContract.REQUEST_TARGET).isEqualTo("request_target");
        assertThat(AippInvocationIdentityContract.BODY_SHA256).isEqualTo("body_sha256");
    }
}
