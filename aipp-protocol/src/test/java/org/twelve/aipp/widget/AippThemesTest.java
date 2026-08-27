package org.twelve.aipp.widget;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AippThemesTest {

    private final AippWidgetSpec spec = new AippWidgetSpec();

    @Test
    void dark_preset_css_vars_complete() {
        Map<String, String> vars = AippThemes.cssVarsForPreset("dark");
        spec.assertThemeCssVarsComplete(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(vars));
    }

    @Test
    void light_preset_css_vars_complete() {
        Map<String, String> vars = AippThemes.cssVarsForPreset("light");
        spec.assertThemeCssVarsComplete(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(vars));
    }

    @Test
    void all_presets_have_complete_css_vars_and_valid_hex() {
        for (String name : AippThemes.presetNames()) {
            Map<String, String> vars = AippThemes.cssVarsForPreset(name);
            spec.assertThemeCssVarsComplete(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(vars));
            spec.assertThemeColorsAreValidHex(AippThemes.theme(name));
        }
    }

    @Test
    void dark_default_matches_json_preset() {
        assertThat(AippWidgetTheme.darkDefault().toCssVars())
                .isEqualTo(AippThemes.cssVarsForPreset("dark"));
    }

    @Test
    void light_default_matches_json_preset() {
        assertThat(AippWidgetTheme.lightDefault().toCssVars())
                .isEqualTo(AippThemes.cssVarsForPreset("light"));
    }

    @Test
    void standard_presets_include_dark_light_and_hatsune() {
        assertThat(AippThemes.presetNames()).contains("dark", "light", "hatsune-miku", "sakura-pop");
        assertThat(AippThemes.presetNames()).doesNotContain("nord", "tokyo-night");
    }

    @Test
    void dark_preset_hex_colors_valid() {
        spec.assertThemeColorsAreValidHex(AippThemes.theme("dark"));
    }
}
