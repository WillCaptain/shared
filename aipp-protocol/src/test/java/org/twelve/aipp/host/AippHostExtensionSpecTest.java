package org.twelve.aipp.host;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AippHostExtensionSpecTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final AippHostExtensionSpec spec = new AippHostExtensionSpec();

    @Test
    void validatesDeclarativeShellContributionsAndInterfaceProvider() {
        Map<String, Object> extensions = spec.extensions(
                List.of(spec.registerBannerIcon(
                        "theme-library", Map.of("en", "Themes", "zh", "主题"),
                        "theme_manager_open", 100)),
                List.of(spec.registerBannerTab(
                        "theme-library-tab", Map.of("en", "Themes"),
                        "theme_manager_open", 100),
                        spec.registerBannerPanelTab(
                                "chat-panel", Map.of("en", "Chat", "zh", "一聊"),
                                "/right-panel/chat-panel.js", 110),
                        spec.registerCountedBannerPanelTab(
                                "alerts", Map.of("en", "Alerts", "zh", "提醒"),
                                "/right-panel/alerts.js", "/right-panel/alerts-badge.js", 120),
                        spec.registerCountedBannerPanelTab(
                                "chat", Map.of("en", "Chat", "zh", "一聊"),
                                "/right-panel/chat.js", "/right-panel/chat-badge.js", true, 130)),
                List.of(spec.provideInterface(
                        "shared.theme.apply/v1", "/theme-interface/theme-interface.js",
                        "theme_current", 30_000)),
                List.of(spec.registerAttachmentSource(
                        "library", Map.of("en", "12th Lib", "zh", "12斋"),
                        "library", "/attachment-source/library.js", true, 100)));

        assertThatNoException().isThrownBy(() -> spec.assertValidHostExtensions(
                JSON.valueToTree(Map.of("host_extensions", extensions))));

        assertThatNoException().isThrownBy(() -> spec.assertValidBannerIcon(
                JSON.valueToTree(spec.registerMainWidgetBannerIcon(
                        "theme-main", Map.of("en", "Themes"), "shell", 100))));
    }

    @Test
    void rejectsExecutableActionsAndUnsafeProviderPaths() {
        assertThatThrownBy(() -> spec.assertValidBannerIcon(JSON.valueToTree(Map.of(
                "operation", "register_banner_icon",
                "id", "bad",
                "label", Map.of("en", "Bad"),
                "icon", "app",
                "action", Map.of("kind", "javascript", "tool", "alert"),
                "order", 0))))
                .hasMessageContaining("kind must be tool or app_main");

        assertThatThrownBy(() -> spec.provideInterface(
                "shared.theme.apply/v1", "/../evil.js", "theme_current", 30_000))
                .hasMessageContaining("safe app-local");

        assertThatThrownBy(() -> spec.provideInterface(
                "shared.example.apply/v1", "/runtime.js", "example_current", 30_000, "named-theme"))
                .hasMessageContaining("fallback_policy");

        assertThatThrownBy(() -> spec.registerBannerPanelTab(
                "bad-panel", Map.of("en", "Bad"), "/../evil.js", 0))
                .hasMessageContaining("safe app-local");

        assertThatThrownBy(() -> spec.registerCountedBannerPanelTab(
                "bad-badge", Map.of("en", "Bad"), "/panel.js", "/../evil.js", 0))
                .hasMessageContaining("safe app-local");

        assertThatThrownBy(() -> spec.registerAttachmentSource(
                "bad-source", Map.of("en", "Bad"), "library", "/../evil.js", true, 0))
                .hasMessageContaining("safe app-local");

        assertThatThrownBy(() -> spec.assertValidBannerIcon(JSON.valueToTree(Map.of(
                "operation", "register_banner_icon",
                "id", "bad-panel-icon",
                "label", Map.of("en", "Bad"),
                "icon", "app",
                "action", Map.of("kind", "panel", "module", "/panel.js"),
                "order", 0))))
                .hasMessageContaining("only valid for banner tabs");
    }

    @Test
    void rejectsDuplicateContributionsWithinOneApp() {
        Map<String, Object> icon = spec.registerBannerIcon(
                "library", Map.of("en", "Library"), "library_open", 10);

        assertThatThrownBy(() -> spec.extensions(
                List.of(icon, icon), List.of(), List.of()))
                .hasMessageContaining("duplicate banner icon id");
    }

    @Test
    void keepsLegacyVersionOneExtensionBlocksValidWithoutAttachmentSources() {
        Map<String, Object> legacy = spec.extensions(List.of(), List.of(), List.of());
        assertThatNoException().isThrownBy(() -> spec.assertValidHostExtensions(
                JSON.valueToTree(Map.of("host_extensions", legacy))));
    }
}
