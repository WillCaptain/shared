package org.twelve.shared.outline.diagnostic;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Language-level syntax diagnostic rewriting for Outline source code.
 *
 * <p>This utility converts raw MSLL grammar messages into actionable editor markers.
 * It is intentionally host-agnostic so app layers (e.g. world-entitir) can pass through
 * diagnostics from the language layer without re-implementing grammar heuristics.</p>
 */
public final class OutlineSyntaxDiagnostics {
    private OutlineSyntaxDiagnostics() {}

    private static final int ERROR_SEVERITY = 8;
    // MSLL emits two slightly different location formats:
    //   "at line: 1, position from 60 to 60"
    //   "at line:1, position: 20 - 21"
    // Accept both. Also note: MSLL's line/position are relative to the
    // offending statement, not the whole source — the statement text is
    // echoed on the next line of the error message (see
    // {@link #extractStatementText}).
    private static final Pattern MSLL_LINE_PAT = Pattern.compile(
            "at line:\\s*(\\d+),\\s*position(?:\\s+from\\s*(\\d+)\\s*to\\s*(\\d+)|\\s*:\\s*(\\d+)\\s*-\\s*(\\d+))");
    private static final Pattern EOF_MISMATCH_PAT = Pattern.compile(
            "END:.*\\$\\s+doesn't\\s+match\\s+any\\s+grammar\\s+definition",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TOKEN_MISMATCH_PAT = Pattern.compile(
            "token:\\s*([A-Z_]+):([^\\s]+)\\s+doesn't\\s+match\\s+any\\s+grammar\\s+definition",
            Pattern.CASE_INSENSITIVE);

    private record ParsedMsllLocation(int line, int from, int to, String cleanMessage) {}
    private record ParsedTokenMismatch(String tokenType, String tokenLexeme) {}
    private record SyntaxDiagnosticContext(String rawMessage, String code, ParsedMsllLocation location) {}
    @FunctionalInterface
    private interface SyntaxDiagnosticRule {
        Map<String, Object> apply(SyntaxDiagnosticContext ctx);
    }

    private static final List<SyntaxDiagnosticRule> RULES = List.of(
            OutlineSyntaxDiagnostics::ruleSemicolonTokenButActuallyMissingDelimiter,
            OutlineSyntaxDiagnostics::ruleUnexpectedEofMissingDelimiter,
            OutlineSyntaxDiagnostics::ruleUnexpectedEofMissingSemicolon,
            OutlineSyntaxDiagnostics::ruleExtraClosingDelimiter,
            OutlineSyntaxDiagnostics::ruleMissingSemicolonAfterBlockBeforeNextToken
    );

    public static Map<String, Object> parseMarker(String msg, String code) {
        if (msg == null) return marker(1, 0, 1, 100, "Syntax error", ERROR_SEVERITY);
        ParsedMsllLocation loc = extractMsllLocation(msg);
        SyntaxDiagnosticContext ctx = new SyntaxDiagnosticContext(msg, code, loc);
        for (SyntaxDiagnosticRule rule : RULES) {
            Map<String, Object> rewritten = rule.apply(ctx);
            if (rewritten != null) return rewritten;
        }
        if (loc != null) {
            // MSLL line/position are relative to the offending statement. Remap
            // them onto absolute (line, column) in the full source whenever we
            // can locate the statement text in {@code code}.
            int[] absStart = remapToAbsolute(code, msg, loc.line(), loc.from());
            int[] absEnd   = remapToAbsolute(code, msg, loc.line(), loc.to());
            if (absStart != null && absEnd != null) {
                int sl = absStart[0], sc = absStart[1];
                int el = absEnd[0],   ec = absEnd[1];
                if (el < sl || (el == sl && ec <= sc)) { el = sl; ec = sc + 1; }
                return marker(sl, sc, el, ec, loc.cleanMessage(), ERROR_SEVERITY);
            }
            return marker(loc.line(), loc.from(), loc.line(), loc.to(), loc.cleanMessage(), ERROR_SEVERITY);
        }
        return marker(1, 0, 1, 100, cleanErrorMessage(msg), ERROR_SEVERITY);
    }

    /**
     * Remap a statement-relative (line, column) MSLL coordinate to an absolute
     * (line, column) in {@code code}, using the statement text that MSLL
     * echoes on the line following the "at line:X, position..." header.
     *
     * @return {@code [absLine, absCol]} (0-based col), or {@code null} if the
     *         statement text could not be located in {@code code}.
     */
    private static int[] remapToAbsolute(String code, String rawMsg, int relLine, int relCol) {
        if (code == null || code.isEmpty() || rawMsg == null) return null;
        String stmt = extractStatementText(rawMsg);
        if (stmt == null || stmt.isEmpty()) return null;
        int stmtStart = code.indexOf(stmt);
        if (stmtStart < 0) return null;
        // Position inside the statement text (MSLL lines are 1-based;
        // most statements fit on one line so typically relLine == 1).
        int inStmt = 0, curLine = 1;
        for (int i = 0; i < stmt.length(); i++) {
            if (curLine == relLine) { inStmt = i + relCol; break; }
            if (stmt.charAt(i) == '\n') curLine++;
        }
        if (curLine < relLine) inStmt = stmt.length();
        int absOffset = stmtStart + Math.min(inStmt, stmt.length());
        return lineColumnFromIndex(code, absOffset);
    }

    /**
     * MSLL error messages emit the failing statement text on the line right
     * after the "at line:X, position..." header. Pull it out so we can
     * locate the statement in the full source and compute absolute coords.
     */
    private static String extractStatementText(String rawMsg) {
        if (rawMsg == null) return null;
        Matcher m = MSLL_LINE_PAT.matcher(rawMsg);
        if (!m.find()) return null;
        int after = rawMsg.indexOf('\n', m.end());
        if (after < 0) return null;
        int nextNl = rawMsg.indexOf('\n', after + 1);
        String stmt = (nextNl < 0 ? rawMsg.substring(after + 1) : rawMsg.substring(after + 1, nextNl));
        stmt = stmt.replace("\r", "").strip();
        return stmt.isEmpty() ? null : stmt;
    }

    public static List<Map<String, Object>> normalizeMarkers(List<Map<String, Object>> markers) {
        if (markers == null || markers.isEmpty()) return markers;
        boolean hasActionableSyntaxHint = markers.stream().anyMatch(m -> {
            Object msg = m.get("message");
            if (!(msg instanceof String s)) return false;
            return s.startsWith("Missing ';'")
                    || s.startsWith("Unexpected end of input;")
                    || s.startsWith("Unexpected ')'")
                    || s.startsWith("Unexpected ']'")
                    || s.startsWith("Unexpected '}'");
        });
        if (!hasActionableSyntaxHint) return markers;
        return markers.stream().filter(m -> {
            Object msg = m.get("message");
            if (!(msg instanceof String s)) return true;
            return !s.contains("END:\\$ doesn't match any grammar definition");
        }).toList();
    }

    private static ParsedMsllLocation extractMsllLocation(String msg) {
        Matcher m = MSLL_LINE_PAT.matcher(msg);
        if (!m.find()) return null;
        int line = Math.max(1, Integer.parseInt(m.group(1)));
        // Either the "from X to Y" group (2,3) matched, or the "X - Y" group (4,5) matched.
        String fromStr = m.group(2) != null ? m.group(2) : m.group(4);
        String toStr   = m.group(3) != null ? m.group(3) : m.group(5);
        int from = Math.max(0, Integer.parseInt(fromStr));
        int to = Math.max(Math.max(0, Integer.parseInt(toStr)), from + 1);
        String cleanMsg = cleanErrorMessage(msg.substring(0, m.start()).stripTrailing());
        return new ParsedMsllLocation(line, from, to, cleanMsg);
    }

    private static ParsedTokenMismatch extractTokenMismatch(String msg) {
        if (msg == null) return null;
        Matcher m = TOKEN_MISMATCH_PAT.matcher(msg);
        if (!m.find()) return null;
        return new ParsedTokenMismatch(m.group(1).toUpperCase(), m.group(2));
    }

    private static Map<String, Object> ruleUnexpectedEofMissingDelimiter(SyntaxDiagnosticContext ctx) {
        String probe = ctx.location() != null ? ctx.location().cleanMessage() : ctx.rawMessage();
        if (!isEofMismatch(probe)) return null;
        String missing = missingClosingDelimiter(ctx.code());
        if (missing == null) return null;
        return eofInsertionMarker(ctx.code(), "Unexpected end of input; missing '" + missing + "'.");
    }

    private static Map<String, Object> ruleUnexpectedEofMissingSemicolon(SyntaxDiagnosticContext ctx) {
        String probe = ctx.location() != null ? ctx.location().cleanMessage() : ctx.rawMessage();
        if (!isEofMismatch(probe)) return null;
        if (!likelyMissingTrailingSemicolon(ctx.code())) return null;
        return eofInsertionMarker(ctx.code(), "Missing ';' at end of statement.");
    }

    private static Map<String, Object> ruleSemicolonTokenButActuallyMissingDelimiter(SyntaxDiagnosticContext ctx) {
        ParsedTokenMismatch mismatch = extractTokenMismatch(ctx.rawMessage());
        if (mismatch == null) return null;
        if (!"SEMICOLON".equalsIgnoreCase(mismatch.tokenType())) return null;
        String missing = missingClosingDelimiter(ctx.code());
        if (missing == null) return null;
        return eofInsertionMarker(ctx.code(), "Unexpected end of input; missing '" + missing + "'.");
    }

    private static Map<String, Object> ruleExtraClosingDelimiter(SyntaxDiagnosticContext ctx) {
        if (ctx.location() == null || ctx.code() == null || ctx.code().isEmpty()) return null;
        int idx = indexFromLineCol(ctx.code(), ctx.location().line(), ctx.location().from());
        if (idx < 0 || idx >= ctx.code().length()) return null;
        char ch = ctx.code().charAt(idx);
        if (ch != ')' && ch != ']' && ch != '}') return null;
        if (!isExtraClosingDelimiterAt(ctx.code(), idx)) return null;
        return marker(ctx.location().line(), ctx.location().from(), ctx.location().line(),
                Math.max(ctx.location().to(), ctx.location().from() + 1),
                "Unexpected '" + ch + "': remove extra closing delimiter.", ERROR_SEVERITY);
    }

    private static Map<String, Object> ruleMissingSemicolonAfterBlockBeforeNextToken(SyntaxDiagnosticContext ctx) {
        ParsedTokenMismatch mismatch = extractTokenMismatch(ctx.rawMessage());
        if (mismatch == null || ctx.location() == null || ctx.code() == null) return null;
        if (!isLikelyStatementStartToken(mismatch.tokenType(), mismatch.tokenLexeme())) return null;
        int roughIdx = indexFromLineCol(ctx.code(), ctx.location().line(), ctx.location().from());
        if (roughIdx < 0) return null;
        int tokenIdx = findTokenStartNear(ctx.code(), mismatch.tokenLexeme(), roughIdx);
        if (tokenIdx < 0) tokenIdx = roughIdx;
        int prev = previousNonWhitespaceIndex(ctx.code(), tokenIdx - 1);
        if (prev < 0 || ctx.code().charAt(prev) != '}') return null;
        int[] lc = lineColumnFromIndex(ctx.code(), prev + 1);
        return marker(lc[0], lc[1], lc[0], lc[1] + 1, "Missing ';' after block expression.", ERROR_SEVERITY);
    }

    private static boolean isEofMismatch(String msg) {
        return msg != null && EOF_MISMATCH_PAT.matcher(msg).find();
    }

    private static String cleanErrorMessage(String msg) {
        if (msg == null) return "Error";
        int cut = msg.indexOf("\nthe possible productions");
        if (cut > 0) msg = msg.substring(0, cut).stripTrailing();
        msg = msg.stripLeading();
        if (msg.length() > 300) msg = msg.substring(0, 300) + "…";
        return msg;
    }

    private static boolean likelyMissingTrailingSemicolon(String code) {
        if (code == null) return false;
        String trimmed = code.stripTrailing();
        if (trimmed.isEmpty()) return false;
        if (trimmed.endsWith(";")) return false;
        if (trimmed.endsWith(".") || trimmed.endsWith(",") || trimmed.endsWith("->")
                || trimmed.endsWith("{") || trimmed.endsWith("(") || trimmed.endsWith("[")
                || trimmed.endsWith(":")) return false;
        return true;
    }

    private static Map<String, Object> eofInsertionMarker(String code, String message) {
        if (code == null || code.isEmpty()) return marker(1, 0, 1, 1, message, ERROR_SEVERITY);
        int line = 1, col = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '\n') {
                line++;
                col = 0;
            } else {
                col++;
            }
        }
        return marker(line, col, line, col + 1, message, ERROR_SEVERITY);
    }

    private static String missingClosingDelimiter(String code) {
        if (code == null || code.isEmpty()) return null;
        ArrayDeque<Character> stack = new ArrayDeque<>();
        boolean inString = false, escape = false;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (inString) {
                if (escape) { escape = false; continue; }
                if (c == '\\') { escape = true; continue; }
                if (c == '"') inString = false;
                continue;
            }
            if (c == '"') { inString = true; continue; }
            if (c == '(' || c == '{' || c == '[') stack.push(c);
            else if (c == ')' || c == '}' || c == ']') {
                if (!stack.isEmpty()) stack.pop();
            }
        }
        if (inString) return "\"";
        if (stack.isEmpty()) return null;
        char open = stack.peek();
        return switch (open) {
            case '(' -> ")";
            case '{' -> "}";
            case '[' -> "]";
            default -> null;
        };
    }

    private static int indexFromLineCol(String code, int line, int col) {
        if (code == null || line < 1 || col < 0) return -1;
        int curLine = 1, curCol = 0;
        for (int i = 0; i < code.length(); i++) {
            if (curLine == line && curCol == col) return i;
            char c = code.charAt(i);
            if (c == '\n') { curLine++; curCol = 0; }
            else curCol++;
        }
        if (curLine == line && curCol == col) return code.length();
        return -1;
    }

    private static int[] lineColumnFromIndex(String code, int idx) {
        if (code == null || idx < 0) return new int[]{1, 0};
        int line = 1, col = 0;
        int stop = Math.min(idx, code.length());
        for (int i = 0; i < stop; i++) {
            char c = code.charAt(i);
            if (c == '\n') { line++; col = 0; }
            else col++;
        }
        return new int[]{line, col};
    }

    private static int previousNonWhitespaceIndex(String code, int fromInclusive) {
        if (code == null) return -1;
        for (int i = Math.min(fromInclusive, code.length() - 1); i >= 0; i--) {
            if (!Character.isWhitespace(code.charAt(i))) return i;
        }
        return -1;
    }

    private static int findTokenStartNear(String code, String tokenLexeme, int roughIdx) {
        if (code == null || code.isEmpty() || tokenLexeme == null || tokenLexeme.isBlank()) return -1;
        String t = tokenLexeme;
        int start = Math.max(0, roughIdx - 120);
        int end = Math.min(code.length(), roughIdx + 240);
        int idx = code.indexOf(t, start);
        int bestIdx = -1, bestDist = Integer.MAX_VALUE;
        while (idx >= 0 && idx < end) {
            char left = idx > 0 ? code.charAt(idx - 1) : '\0';
            int rightIdx = idx + t.length();
            char right = rightIdx < code.length() ? code.charAt(rightIdx) : '\0';
            boolean leftOk = left == '\0' || (!Character.isLetterOrDigit(left) && left != '_');
            boolean rightOk = right == '\0' || (!Character.isLetterOrDigit(right) && right != '_');
            if (leftOk && rightOk) {
                int dist = Math.abs(idx - roughIdx);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestIdx = idx;
                }
            }
            idx = code.indexOf(t, idx + 1);
        }
        return bestIdx;
    }

    private static boolean isExtraClosingDelimiterAt(String code, int idx) {
        int paren = 0, brace = 0, bracket = 0;
        boolean inString = false, escape = false;
        for (int i = 0; i <= idx && i < code.length(); i++) {
            char c = code.charAt(i);
            if (inString) {
                if (escape) { escape = false; continue; }
                if (c == '\\') { escape = true; continue; }
                if (c == '"') inString = false;
                continue;
            }
            if (c == '"') { inString = true; continue; }
            switch (c) {
                case '(' -> paren++;
                case ')' -> paren--;
                case '{' -> brace++;
                case '}' -> brace--;
                case '[' -> bracket++;
                case ']' -> bracket--;
                default -> {}
            }
        }
        char ch = code.charAt(idx);
        return (ch == ')' && paren < 0) || (ch == '}' && brace < 0) || (ch == ']' && bracket < 0);
    }

    private static boolean isLikelyStatementStartToken(String tokenType, String tokenLexeme) {
        if (tokenType == null) return false;
        if ("LET".equalsIgnoreCase(tokenType) || "VAR".equalsIgnoreCase(tokenType)
                || "RETURN".equalsIgnoreCase(tokenType) || "IF".equalsIgnoreCase(tokenType)
                || "MATCH".equalsIgnoreCase(tokenType)) return true;
        if ("ID".equalsIgnoreCase(tokenType) || "SYMBOL".equalsIgnoreCase(tokenType)) return true;
        if (tokenLexeme == null) return false;
        return "let".equals(tokenLexeme) || "var".equals(tokenLexeme)
                || "return".equals(tokenLexeme) || "if".equals(tokenLexeme) || "match".equals(tokenLexeme);
    }

    private static Map<String, Object> marker(int sl, int sc, int el, int ec, String msg, int sev) {
        return Map.of(
                "startLine", sl,
                "startColumn", sc,
                "endLine", el,
                "endColumn", ec,
                "message", msg,
                "severity", sev
        );
    }
}
