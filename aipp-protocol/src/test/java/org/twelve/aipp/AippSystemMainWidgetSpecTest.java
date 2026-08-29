package org.twelve.aipp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AippSystemMainWidgetSpecTest {

    private final ObjectMapper json = new ObjectMapper();
    private final AippAppSpec spec = new AippAppSpec();

    @Test
    void appUsingHostAboutWidgetMayHaveNoAppOwnedWidgets() throws Exception {
        var widgets = json.readTree("{\"app\":\"chat-one\",\"widgets\":[]}");
        var app = json.readTree("""
                {
                  "app_id":"chat-one",
                  "listing":"private",
                  "main_widget_type":"sys.app-info",
                  "host_extensions":{"banner_tabs":[{"id":"one-chat"}]}
                }
                """);

        spec.assertValidWidgetsApiStructure(widgets, app);
    }

    @Test
    void emptyWidgetsRequireTheHostAboutMainWidget() throws Exception {
        var widgets = json.readTree("{\"app\":\"chat-one\",\"widgets\":[]}");
        var customMain = json.readTree("{\"app_id\":\"chat-one\",\"main_widget_type\":\"chat-one\"}");
        var missingMain = json.readTree("{\"app_id\":\"chat-one\"}");

        assertThatThrownBy(() -> spec.assertValidWidgetsApiStructure(widgets, customMain))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> spec.assertValidWidgetsApiStructure(widgets, missingMain))
                .isInstanceOf(AssertionError.class);
    }
}
