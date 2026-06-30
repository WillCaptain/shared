package org.twelve.aipp.widget;

/**
 * AIPP standard widget theme configuration.
 *
 * <p>Values are loaded from classpath {@code aipp-themes.json} (generated from
 * {@code shared/theme/aipp-themes.json}). Host applies {@link #toCssVars()} on
 * {@code :root}; widgets use shared {@code .aipp-*} primitives only — no per-app CSS.
 *
 * @see AippThemes
 * @see AippWidgetSpec#assertThemeCssVarsComplete(com.fasterxml.jackson.databind.JsonNode)
 */
public record AippWidgetTheme(
        String background,
        String surface,
        String surface2,
        String surface3,
        String text,
        String textDim,
        String textMuted,
        String border,
        String border2,
        String accent,
        String accentHover,
        String accentGlow,
        String active,
        String danger,
        String success,
        String warning,
        String info,
        String font,
        String fontMono,
        int fontSize,
        int fontSizeSm,
        int fontSizeLg,
        int radius,
        int radiusSm,
        int radiusLg,
        int radiusPill,
        String language,
        boolean darkMode
) {

    /** Default dark preset (production). */
    public static AippWidgetTheme darkDefault() {
        return AippThemes.theme("dark");
    }

    /** Built-in light preset. */
    public static AippWidgetTheme lightDefault() {
        return AippThemes.theme("light");
    }

    /**
     * Full {@code --aipp-*} CSS variable map for DOM injection.
     * {@code language} is passed via {@code data-aipp-language}, not CSS.
     */
    public java.util.Map<String, String> toCssVars() {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        map.put("--aipp-bg",             background);
        map.put("--aipp-surface",        surface);
        map.put("--aipp-surface2",       surface2);
        map.put("--aipp-surface3",       surface3);
        map.put("--aipp-text",           text);
        map.put("--aipp-text-dim",       textDim);
        map.put("--aipp-text-muted",     textMuted);
        map.put("--aipp-border",         border);
        map.put("--aipp-border2",        border2);
        map.put("--aipp-accent",         accent);
        map.put("--aipp-accent-hover",   accentHover);
        map.put("--aipp-accent-glow",    accentGlow);
        map.put("--aipp-active",         active);
        map.put("--aipp-danger",         danger);
        map.put("--aipp-success",        success);
        map.put("--aipp-warning",        warning);
        map.put("--aipp-info",           info);
        map.put("--aipp-font",           font);
        map.put("--aipp-font-mono",      fontMono);
        map.put("--aipp-font-size",      fontSize + "px");
        map.put("--aipp-font-size-sm",   fontSizeSm + "px");
        map.put("--aipp-font-size-lg",   fontSizeLg + "px");
        map.put("--aipp-radius",         radius + "px");
        map.put("--aipp-radius-sm",      radiusSm + "px");
        map.put("--aipp-radius-lg",      radiusLg + "px");
        map.put("--aipp-radius-pill",    radiusPill + "px");
        return map;
    }
}
