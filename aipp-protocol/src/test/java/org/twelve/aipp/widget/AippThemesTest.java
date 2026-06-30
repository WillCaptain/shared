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
    void dark_preset_hex_colors_valid() {
        spec.assertThemeColorsAreValidHex(AippThemes.theme("dark"));
    }
}
