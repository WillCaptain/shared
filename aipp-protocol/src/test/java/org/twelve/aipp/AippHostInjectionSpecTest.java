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
                  "host_base_url": "http://127.0.0.1:8090",
                  "app_id": "decision-reactor",
                  "env": "production",
                  "host_event_callback_url": "http://127.0.0.1:8090/api/host/event-callbacks/decision-reactor"
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
