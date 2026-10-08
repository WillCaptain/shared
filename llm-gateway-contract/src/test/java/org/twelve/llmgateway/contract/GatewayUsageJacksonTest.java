package org.twelve.llmgateway.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GatewayUsageJacksonTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void roundTripDoesNotEmitConsistentAndIgnoresUnknownConsistent() throws Exception {
        GatewayUsage usage = new GatewayUsage("p", "m", 3L, 1L, 2L, 4L, null,
                GatewayUsage.REPORTED, "{}", "v1");
        assertTrue(usage.isConsistent());
        String encoded = json.writeValueAsString(usage);
        assertFalse(encoded.contains("consistent"), encoded);

        GatewayUsage decoded = json.readValue(
                "{\"provider\":\"p\",\"model\":\"m\",\"inputTokens\":3,\"cachedInputTokens\":1,"
                        + "\"uncachedInputTokens\":2,\"outputTokens\":4,\"reasoningTokens\":null,"
                        + "\"status\":\"reported\",\"rawUsage\":\"{}\",\"adapterVersion\":\"v1\","
                        + "\"consistent\":true}",
                GatewayUsage.class);
        assertEquals("p", decoded.provider());
        assertEquals(3L, decoded.inputTokens());
    }
}
