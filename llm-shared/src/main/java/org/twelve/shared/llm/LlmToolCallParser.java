package org.twelve.shared.llm;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@code tool_calls} from OpenAI-compatible Chat Completions HTTP responses.
 */
public final class LlmToolCallParser {

    private static final Pattern TC_ID   = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TC_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    private static final ObjectMapper JACKSON = new ObjectMapper();

    private LlmToolCallParser() {}

    public static String extractToolCallsArray(String responseBody) {
        int keyIdx = responseBody.indexOf("\"tool_calls\"");
        if (keyIdx < 0) return "[]";
        int arrStart = responseBody.indexOf('[', keyIdx);
        if (arrStart < 0) return "[]";
        return extractBalanced(responseBody, arrStart, '[', ']');
    }

    public static List<ToolCallInfo> parseToolCalls(String toolCallsJson) {
        List<ToolCallInfo> result = new ArrayList<>();
        if (toolCallsJson == null || toolCallsJson.isBlank()) return result;

        int i = 0;
        while (i < toolCallsJson.length()) {
            int objStart = toolCallsJson.indexOf('{', i);
            if (objStart < 0) break;

            String obj = extractBalanced(toolCallsJson, objStart, '{', '}');
            i = objStart + obj.length();

            Matcher idM   = TC_ID.matcher(obj);
            Matcher nameM = TC_NAME.matcher(obj);

            String id   = idM.find()   ? idM.group(1)  : java.util.UUID.randomUUID().toString();
            String name = nameM.find() ? nameM.group(1) : "";
            // Non-recursive scan: a regex like "(?:[^"\\]|\\.)*" overflows the JVM stack
            // on long argument payloads (Java compiles grouped quantifiers to recursion).
            String args = firstStringField(obj, "arguments");
            if (args == null || args.isBlank()) args = "{}";

            if (!name.isBlank()) result.add(new ToolCallInfo(id, name, args));
        }
        return result;
    }

    public static String extractBalanced(String s, int start, char open, char close) {
        int     depth    = 0;
        boolean inString = false;
        boolean escaped  = false;

        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (inString) {
                if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
            } else {
                if      (c == '"')   inString = true;
                else if (c == open)  depth++;
                else if (c == close) {
                    depth--;
                    if (depth == 0) return s.substring(start, i + 1);
                }
            }
        }
        return s.substring(start);
    }

    static String unescape(String s) {
        return s.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    /**
     * Index of the closing quote for a JSON string whose opening quote sits at
     * {@code openQuote}, honoring backslash escapes; {@code -1} if unterminated.
     * Linear scan — never recurses, so it is safe on arbitrarily long strings.
     */
    static int findStringEnd(String s, int openQuote) {
        boolean escaped = false;
        for (int i = openQuote + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped)       { escaped = false; continue; }
            if (c == '\\')     { escaped = true;  continue; }
            if (c == '"')        return i;
        }
        return -1;
    }

    /**
     * Returns the (unescaped) value of the first {@code "key": "..."} JSON string
     * field in {@code s}, or {@code null} if absent. Replaces the catastrophic
     * {@code "(?:[^"\\]|\\.)*"} regex, which recurses one stack frame per character
     * and overflows the JVM stack on long values.
     */
    public static String firstStringField(String s, String key) {
        if (s == null) return null;
        String needle = "\"" + key + "\"";
        int k = s.indexOf(needle);
        while (k >= 0) {
            int j = k + needle.length();
            while (j < s.length() && Character.isWhitespace(s.charAt(j))) j++;
            if (j < s.length() && s.charAt(j) == ':') {
                j++;
                while (j < s.length() && Character.isWhitespace(s.charAt(j))) j++;
                if (j < s.length() && s.charAt(j) == '"') {
                    int end = findStringEnd(s, j);
                    if (end > j) return unescape(s.substring(j + 1, end));
                }
            }
            k = s.indexOf(needle, k + needle.length());
        }
        return null;
    }

    public record ToolCallInfo(String id, String name, String arguments) {

        @SuppressWarnings("unchecked")
        public Map<String, Object> parsedArgs() {
            if (arguments == null || arguments.isBlank()) return new LinkedHashMap<>();
            try {
                Object parsed = JACKSON.readValue(arguments, Object.class);
                if (parsed instanceof Map<?, ?> m) {
                    Map<String, Object> out = new LinkedHashMap<>();
                    for (var e : m.entrySet()) {
                        if (e.getKey() != null) out.put(e.getKey().toString(), e.getValue());
                    }
                    return out;
                }
            } catch (Exception ignored) {
                // regex fallback below
            }
            return parseSimpleArgsJsonFallback(arguments);
        }

        private static Map<String, Object> parseSimpleArgsJsonFallback(String json) {
            Map<String, Object> result = new LinkedHashMap<>();
            if (json == null) return result;
            // Match only the key + delimiter; values are scanned manually so that a
            // long string value can never trigger the recursive-regex stack overflow.
            Pattern keyPat = Pattern.compile("\"([^\"]+)\"\\s*:\\s*");
            Matcher m = keyPat.matcher(json);
            while (m.find()) {
                String key = m.group(1);
                int p = m.end();
                if (p >= json.length()) break;
                char c = json.charAt(p);
                if (c == '"') {
                    int end = findStringEnd(json, p);
                    if (end > p) result.put(key, unescape(json.substring(p + 1, end)));
                } else if (c == '-' || Character.isDigit(c)) {
                    int q = p;
                    while (q < json.length()
                            && (Character.isDigit(json.charAt(q)) || json.charAt(q) == '-'
                                || json.charAt(q) == '.' || json.charAt(q) == 'e'
                                || json.charAt(q) == 'E' || json.charAt(q) == '+')) q++;
                    String num = json.substring(p, q);
                    try {
                        result.put(key, num.contains(".") || num.contains("e") || num.contains("E")
                                ? Double.parseDouble(num) : Long.parseLong(num));
                    } catch (NumberFormatException ignore) {
                        // not a clean number — skip this key
                    }
                } else if (json.startsWith("true", p)) {
                    result.put(key, Boolean.TRUE);
                } else if (json.startsWith("false", p)) {
                    result.put(key, Boolean.FALSE);
                }
                // Nested objects/arrays are not flattened here (matches prior behaviour).
            }
            return result;
        }
    }
}
