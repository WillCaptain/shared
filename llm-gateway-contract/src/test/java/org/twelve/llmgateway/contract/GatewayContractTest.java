package org.twelve.llmgateway.contract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GatewayContractTest {
    @Test void acceptsModelMaximumButRejectsLargerOutputBudgets() {
        var messages = java.util.List.<java.util.Map<String, Object>>of(java.util.Map.of("role", "user", "content", "hi"));
        assertEquals(393_216, new ChatInvocationRequest(null, "model", messages, null,
                ChatInvocationRequest.MAX_TOKENS, null, null, null).maxTokens());
        assertThrows(IllegalArgumentException.class, () -> new ChatInvocationRequest(null, "model",
                messages, null, ChatInvocationRequest.MAX_TOKENS + 1, null, null, null));
    }

    @Test void metadataContainsNoIdentityAndNormalizesIntent() {
        GatewayRequestMetadata metadata = new GatewayRequestMetadata(" s ", null, " feature ", null);
        assertEquals("s", metadata.sessionId());
        assertEquals("feature", metadata.featureCode());
        assertEquals("unknown", metadata.callType());
        assertThrows(NoSuchMethodException.class,
                () -> GatewayRequestMetadata.class.getDeclaredMethod("userId"));
    }
}
