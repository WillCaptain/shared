package org.twelve.aipp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AippLocalesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void normalize_primarySubtag() {
        assertThat(AippLocales.normalize("zh-CN")).isEqualTo("zh");
        assertThat(AippLocales.normalize("en_US")).isEqualTo("en");
        assertThat(AippLocales.normalize(null)).isEqualTo("en");
        assertThat(AippLocales.normalize("  ")).isEqualTo("en");
    }

    @Test
    void resolve_prefersRequestedLanguageThenEn() {
        Map<String, String> labels = AippLocales.ofEnZh(
                "Invalid instruction: Once required.",
                "无效指令：需要 Once。");

        assertThat(AippLocales.resolve(labels, "zh")).contains("无效指令");
        assertThat(AippLocales.resolve(labels, "zh-CN")).contains("无效指令");
        assertThat(AippLocales.resolve(labels, "en")).contains("Invalid instruction");
        assertThat(AippLocales.resolve(labels, "fr")).contains("Invalid instruction");
    }

    @Test
    void replyLanguage_followsUserMessageOverSessionUi() {
        assertThat(AippLocales.replyLanguage("zh", "What apps are installed?"))
                .isEqualTo("en");
        assertThat(AippLocales.replyLanguage("en", "有哪些应用？"))
                .isEqualTo("zh");
        // Short shell / no signal → keep session language
        assertThat(AippLocales.replyLanguage("zh", "pwd")).isEqualTo("zh");
        assertThat(AippLocales.replyLanguage("en", "ls")).isEqualTo("en");
    }

    @Test
    void resolve_fromJsonObject() {
        ObjectNode n = JSON.createObjectNode();
        n.put("en", "Hello");
        n.put("zh", "你好");
        assertThat(AippLocales.resolve(n, "zh")).isEqualTo("你好");
        assertThat(AippLocales.resolve(n, "en")).isEqualTo("Hello");
    }

    @Test
    void assertValidLocalizedLabels_requiresEn() {
        AippAppSpec spec = new AippAppSpec();
        ObjectNode good = JSON.createObjectNode();
        good.put("en", "New recipe");
        good.put("zh", "新建菜谱");
        spec.assertValidLocalizedLabels("display_labels", good);

        ObjectNode missingEn = JSON.createObjectNode();
        missingEn.put("zh", "只有中文");
        org.junit.jupiter.api.Assertions.assertThrows(
                AssertionError.class,
                () -> spec.assertValidLocalizedLabels("display_labels", missingEn));
    }
}
