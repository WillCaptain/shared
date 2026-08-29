package org.twelve.aipp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AippHostExtensionOnlyAppSpecTest {

    private final ObjectMapper json = new ObjectMapper();
    private final AippAppSpec spec = new AippAppSpec();

    @Test
    void privateBannerExtensionMayHaveNoWidgets() throws Exception {
        var widgets = json.readTree("{\"app\":\"chat-one\",\"widgets\":[]}");
        var app = json.readTree("""
                {
                  "app_id":"chat-one",
                  "listing":"private",
                  "host_extensions":{"banner_tabs":[{"id":"one-chat"}]}
                }
                """);

        spec.assertValidWidgetsApiStructure(widgets, app);
    }

    @Test
    void publicOrEntrylessAppCannotUseTheException() throws Exception {
        var widgets = json.readTree("{\"app\":\"chat-one\",\"widgets\":[]}");
        var publicApp = json.readTree("{\"app_id\":\"chat-one\",\"listing\":\"public\"}");
        var entryless = json.readTree("{\"app_id\":\"chat-one\",\"listing\":\"private\"}");

        assertThatThrownBy(() -> spec.assertValidWidgetsApiStructure(widgets, publicApp))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> spec.assertValidWidgetsApiStructure(widgets, entryless))
                .isInstanceOf(AssertionError.class);
    }
}
