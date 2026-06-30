package org.twelve.aipp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AippLlmConfigSpecTest {

    private final AippLlmConfigSpec spec = new AippLlmConfigSpec();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void validGetResponse() throws Exception {
        spec.assertValidLlmConfigResponse(json.readTree("""
                {
                  "ok": true,
                  "source": "instance",
                  "config": {
                    "api_key": "sk-test",
                    "base_url": "https://api.deepseek.com/v1",
                    "model": "deepseek-chat",
                    "timeout_seconds": 120,
                    "vision_mode": "auto"
                  }
                }
                """));
    }

    @Test
    void notConfiguredResponse() throws Exception {
        spec.assertValidLlmConfigResponse(json.readTree("""
                {"ok": false, "error": "llm_not_configured", "message": "…"}
                """));
    }

    @Test
    void validInstancePut() throws Exception {
        spec.assertValidLlmConfigInstancePutRequest(json.readTree("""
                {
                  "api_key": "sk-x",
                  "base_url": "https://api.openai.com/v1",
                  "model": "gpt-4o"
                }
                """));
    }

    @Test
    void validUserPut() throws Exception {
        spec.assertValidLlmConfigUserPutRequest(json.readTree("""
                {
                  "user_id": "001",
                  "api_key": "sk-x",
                  "base_url": "https://api.openai.com/v1",
                  "model": "gpt-4o"
                }
                """));
    }
}
