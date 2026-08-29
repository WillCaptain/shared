package org.twelve.llmgateway.contract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GatewayContractTest {
    @Test void metadataContainsNoIdentityAndNormalizesIntent() {
        GatewayRequestMetadata metadata = new GatewayRequestMetadata(" s ", null, " feature ", null);
        assertEquals("s", metadata.sessionId());
        assertEquals("feature", metadata.featureCode());
        assertEquals("unknown", metadata.callType());
        assertThrows(NoSuchMethodException.class,
                () -> GatewayRequestMetadata.class.getDeclaredMethod("userId"));
    }
}
