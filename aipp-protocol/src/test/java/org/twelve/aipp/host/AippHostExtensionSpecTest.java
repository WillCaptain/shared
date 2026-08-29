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
                        "theme_manager_open", 100)),
                List.of(spec.provideInterface(
                        "shared.theme.apply/v1", "/theme-interface/theme-interface.js",
                        "theme_current", 30_000)));

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
    }

    @Test
    void rejectsDuplicateContributionsWithinOneApp() {
        Map<String, Object> icon = spec.registerBannerIcon(
                "library", Map.of("en", "Library"), "library_open", 10);

        assertThatThrownBy(() -> spec.extensions(
                List.of(icon, icon), List.of(), List.of()))
                .hasMessageContaining("duplicate banner icon id");
    }
}
