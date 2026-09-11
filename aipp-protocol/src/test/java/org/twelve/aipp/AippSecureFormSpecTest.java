package org.twelve.aipp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AippSecureFormSpecTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AippSecureFormSpec spec = new AippSecureFormSpec();

    @Test
    void acceptsLocalizedMultiViewSchemaWithoutValues() throws Exception {
        JsonNode data = mapper.readTree("""
                {
                  "schema": "aipp.secure-form/v1",
                  "form_id": "form_opaque",
                  "title": {"en":"Sign in","zh":"登录"},
                  "views": [{
                    "id": "primary",
                    "fields": [
                      {"name":"account","type":"text","required":true},
                      {"name":"secret","type":"password","required":true,"sensitive":true}
                    ]
                  }],
                  "actions": [
                    {"id":"cancel","role":"cancel"},
                    {"id":"submit","role":"submit"}
                  ]
                }
                """);
        assertThatNoException().isThrownBy(() -> spec.assertValidSchema(data));
        assertThat(AippSystemWidget.SECURE_FORM).isEqualTo("sys.secure-form");
    }

    @Test
    void rejectsSensitiveDefaultsThatWouldCrossHostBoundary() throws Exception {
        JsonNode data = mapper.readTree("""
                {
                  "schema": "aipp.secure-form/v1",
                  "form_id": "form_opaque",
                  "views": [{"id":"primary","fields":[{
                    "name":"secret","type":"password","sensitive":true,"default_value":"leak"
                  }]}],
                  "actions": [{"id":"submit","role":"submit"}]
                }
                """);
        assertThatThrownBy(() -> spec.assertValidSchema(data))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("sensitive fields");
    }
}
