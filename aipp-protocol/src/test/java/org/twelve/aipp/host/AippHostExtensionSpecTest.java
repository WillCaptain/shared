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
    void acceptsHelpActionThatTargetsAnAppOwnedPanel() {
        Map<String, Object> contribution = spec.helpContribution(
                "chat", List.of("introduce together"), Map.of("en", "Together"),
                Map.of("en", "Open the collaboration panel."), List.of(),
                List.of(Map.of("kind", "panel", "extension_id", "one-chat",
                        "label", Map.of("en", "Go to Together"))));
        assertThatNoException().isThrownBy(() -> spec.assertValidHostExtensions(
                JSON.valueToTree(Map.of("host_extensions", Map.of(
                        "schema_version", 1,
                        "banner_icons", List.of(),
                        "banner_tabs", List.of(),
                        "interface_providers", List.of(),
                        "help_contributions", List.of(contribution))))));
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

    @Test
    void validatesHelpContributionsAsAippOwnedUserIntros() {
        Map<String, Object> extensions = spec.extensions(
                List.of(), List.of(), List.of(), List.of(),
                List.of(spec.helpContribution(
                        "entitir",
                        List.of("introduce entitir", "entitir 是什么"),
                        Map.of("en", "entitir — typed decisions", "zh", "entitir — 有类型的决策"),
                        Map.of("en",
                                "Ontology in, executable chains out. Outline VirtualSet expressions "
                                        + "give the LLM typed structure for custom decisions.",
                                "zh",
                                "本体进，可执行决策链出。Outline VirtualSet 给 LLM 可类型检查的结构。"),
                        List.of(Map.of("en", "Open the world list to start.", "zh", "打开世界列表开始。")),
                        List.of(spec.helpOpenMainAction(
                                Map.of("en", "Open entitir", "zh", "打开 entitir"))))));
        assertThatNoException().isThrownBy(() -> spec.assertValidHostExtensions(
                JSON.valueToTree(Map.of("host_extensions", extensions))));
    }

    @Test
    void acceptsAippOwnedPrecisePositionResolver() {
        Map<String, Object> contribution = spec.helpContribution(
                "chat-position", List.of("go to chat"), Map.of("en", "Open chat"),
                Map.of("en", "Resolve and open the exact conversation."), List.of(),
                List.of(Map.of(
                        "kind", "tool", "tool", "chat_conversation_open",
                        "forward_question_as", "query",
                        "label", Map.of("en", "Open matching chat"))));

        assertThatNoException().isThrownBy(() -> spec.assertValidHelpContribution(
                JSON.valueToTree(contribution)));
    }

    @Test
    void rejectsHelpContributionWithoutEnglishSummary() {
        assertThatThrownBy(() -> spec.helpContribution(
                "x", List.of("x"), Map.of("en", "X"), Map.of("zh", "只有中文"),
                List.of(), List.of()))
                .hasMessageContaining("summary.en is required");
    }
}
