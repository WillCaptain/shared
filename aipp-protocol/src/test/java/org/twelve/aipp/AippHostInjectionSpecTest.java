package org.twelve.aipp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AippHostInjectionSpecTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final AippHostInjectionSpec spec = new AippHostInjectionSpec();

    @Test
    void validPutRequest() throws Exception {
        spec.assertValidHostBindingsPutRequest(JSON.readTree("""
                {
                  "host_id": "worldone",
                  "app_id": "decision-reactor",
                  "env": "production"
                }
                """));
    }

    @Test
    void validPutResponse() throws Exception {
        spec.assertValidHostBindingsPutResponse(JSON.readTree("{\"ok\":true}"));
    }

    @Test
    void validGetResponse() throws Exception {
        spec.assertValidHostBindingsGetResponse(JSON.readTree("""
                {"ok":true,"bindings":{"env":"staging"}}
                """));
    }
}
