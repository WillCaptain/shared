package org.twelve.aipp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * AIPP localization helpers — see {@code spec/localization.md}.
 *
 * <p>Session language is Host SSOT. User-facing strings are {@code language → text}
 * maps with required {@code en} fallback.
 */
public final class AippLocales {

    public static final String DEFAULT_LANGUAGE = "en";

    private AippLocales() {}

    /**
     * Normalize an IETF / BCP-47-ish tag to a primary language subtag
     * ({@code zh-CN} → {@code zh}, {@code en_US} → {@code en}).
     * Blank / null → {@link #DEFAULT_LANGUAGE}.
     */
    public static String normalize(String language) {
        if (language == null || language.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        String raw = language.strip().replace('_', '-');
        int dash = raw.indexOf('-');
        String primary = (dash < 0 ? raw : raw.substring(0, dash)).toLowerCase(Locale.ROOT);
        return primary.isBlank() ? DEFAULT_LANGUAGE : primary;
    }

    /**
     * Resolve a LocalizedString map for {@code language}.
     * Order: exact → primary subtag → {@code en} → first non-blank value → {@code ""}.
     */
    public static String resolve(Map<String, String> labels, String language) {
        if (labels == null || labels.isEmpty()) {
            return "";
        }
        String lang = normalize(language);
        String exact = nonBlank(labels.get(lang));
        if (exact != null) return exact;
        // Also try original keys that might already be full tags stored as-is
        for (Map.Entry<String, String> e : labels.entrySet()) {
            if (e.getKey() != null && normalize(e.getKey()).equals(lang)) {
                String v = nonBlank(e.getValue());
                if (v != null) return v;
            }
        }
        String en = nonBlank(labels.get(DEFAULT_LANGUAGE));
        if (en != null) return en;
        for (String v : labels.values()) {
            String nb = nonBlank(v);
            if (nb != null) return nb;
        }
        return "";
    }

    /** Resolve from a JSON object node ({@code "en":"...","zh":"..."}). */
    public static String resolve(JsonNode labelsNode, String language) {
        return resolve(toMap(labelsNode), language);
    }

    /**
     * Build a LocalizedString map from a JSON object. Non-textual values are skipped.
     */
    public static Map<String, String> toMap(JsonNode labelsNode) {
        Map<String, String> out = new LinkedHashMap<>();
        if (labelsNode == null || !labelsNode.isObject()) {
            return out;
        }
        labelsNode.fields().forEachRemaining(e -> {
            JsonNode v = e.getValue();
            if (v != null && v.isTextual()) {
                String t = v.asText();
                if (t != null && !t.isBlank()) {
                    out.put(normalize(e.getKey()), t.strip());
                }
            }
        });
        return out;
    }

    /**
     * Convenience for Host/AIPP in-code catalogs.
     *
     * @param en required English text
     * @param zh optional Chinese text (may be null)
     */
    public static Map<String, String> ofEnZh(String en, String zh) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(DEFAULT_LANGUAGE, Objects.requireNonNull(en, "en"));
        if (zh != null && !zh.isBlank()) {
            m.put("zh", zh.strip());
        }
        return Map.copyOf(m);
    }

    /**
     * Language for <strong>assistant / Host chat replies</strong> on this turn.
     *
     * <p>Session {@code language} selects Host system-prompt variant and UI chrome
     * (LocalizedString for widgets/labels). Reply language follows the user's
     * message when a clear {@code zh}/{@code en} signal is present; otherwise
     * falls back to session language. See {@code spec/localization.md} §2.1.
     */
    public static String replyLanguage(String sessionLanguage, String userMessage) {
        String session = normalize(sessionLanguage);
        String detected = detectPrimaryLanguage(userMessage);
        return detected != null ? detected : session;
    }

    /**
     * Cheap primary-language hint for product languages {@code zh} / {@code en}.
     * Returns {@code null} when the message has no clear signal (short shell
     * tokens, code, mixed/empty) so callers keep session language.
     */
    public static String detectPrimaryLanguage(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        int cjk = 0;
        int latin = 0;
        for (int i = 0; i < userMessage.length(); ) {
            int cp = userMessage.codePointAt(i);
            i += Character.charCount(cp);
            if (isCjk(cp)) {
                cjk++;
            } else if (isLatinLetter(cp)) {
                latin++;
            }
        }
        if (cjk == 0 && latin == 0) {
            return null;
        }
        // Prefer Han when present with meaningful weight (UI may be en, user asks 中文).
        if (cjk >= 2 && cjk >= latin) {
            return "zh";
        }
        if (cjk >= 1 && latin == 0) {
            return "zh";
        }
        // English phrases — not short shell tokens like "pwd" / "ls" (keep session lang).
        if (cjk == 0 && latin >= 8) {
            return "en";
        }
        if (cjk == 0 && latin >= 4 && hasAsciiWordBreak(userMessage)) {
            return "en";
        }
        return null;
    }

    private static boolean hasAsciiWordBreak(String s) {
        return s.indexOf(' ') >= 0 || s.indexOf('\t') >= 0 || s.indexOf('\n') >= 0;
    }

    /** Han ideographs used as a zh signal (product languages: zh / en). */
    private static boolean isCjk(int cp) {
        Character.UnicodeBlock b = Character.UnicodeBlock.of(cp);
        return b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || b == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    private static boolean isLatinLetter(int cp) {
        return (cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z');
    }

    private static String nonBlank(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }
}
