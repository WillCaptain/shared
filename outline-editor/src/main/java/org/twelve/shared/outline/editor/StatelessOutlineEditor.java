package org.twelve.shared.outline.editor;

import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.exception.GCPError;
import org.twelve.gcp.meta.CompletionItem;
import org.twelve.outline.meta.MetaExtractor;
import org.twelve.gcp.meta.ModuleMeta;
import org.twelve.gcp.meta.StatelessInference;
import org.twelve.gcp.outline.Outline;
import org.twelve.gcp.outline.projectable.Function;
import org.twelve.msll.exception.AggregateGrammarSyntaxException;
import org.twelve.msll.exception.GrammarSyntaxException;
import org.twelve.outline.OutlineParser;
import org.twelve.shared.outline.diagnostic.OutlineSyntaxDiagnostics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic stateless Monaco editor backend for the {@code prelude_length} wire
 * contract shared by {@code outline-lang.js} and every Outline host.
 *
 * <p>Completion semantics live in {@link MetaExtractor}; this class only
 * orchestrates parse, preamble cache, symbol seeding, and marker shaping.
 * Host apps supply domain preamble text and HTTP routing — not language logic.
 */
public final class StatelessOutlineEditor {

    private static final int PRELUDE_CACHE_MAX = 32;
    private static final OutlineParser PARSER = new OutlineParser();
    private static final Map<String, ASF> PRELUDE_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ASF> e) {
                    return size() > PRELUDE_CACHE_MAX;
                }
            });

    private StatelessOutlineEditor() {}

    public static String[] splitPreludeBody(Map<String, Object> body, String code) {
        Object plObj = body == null ? null : body.get("prelude_length");
        int pl = (plObj instanceof Number n) ? n.intValue() : 0;
        return MetaExtractor.splitPreludeWire(pl, code);
    }

    public static ASF preambleAsf(String preludeText) {
        if (preludeText == null || preludeText.isEmpty()) return null;
        String key = preludeKey(preludeText);
        ASF cached = PRELUDE_CACHE.get(key);
        if (cached != null) return cached;
        ASF asf = new ASF();
        try {
            PARSER.parseResilient(asf, preludeText);
            try {
                asf.infer();
            } catch (Exception ignored) {
            }
            PRELUDE_CACHE.put(key, asf);
            return asf;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Parse {@code userCode} into a fresh fork with symbols from a freshly inferred
     * preamble ASF. The preamble is not taken from {@link #PRELUDE_CACHE}, so user
     * inference cannot mutate the cached snapshot, but it is still inferred before
     * seeding so declarations such as {@code VirtualSet}, {@code Decision}, and
     * host collection repos have real outlines rather than placeholders.
     */
    public static StatelessInference inferWithPrelude(String preludeText, String userCode) {
        ASF preamble = null;
        if (preludeText != null && !preludeText.isEmpty()) {
            preamble = new ASF();
            try {
                PARSER.parseResilient(preamble, preludeText);
                try {
                    preamble.infer();
                } catch (Throwable ignored) {
                }
            } catch (Throwable t) {
                preamble = null;
            }
        }
        ASF fork = new ASF();
        AST userAst;
        try {
            userAst = PARSER.parseResilient(fork, userCode);
        } catch (Throwable t) {
            return null;
        }
        MetaExtractor.seedUserAstFromPreamble(preamble, userAst);
        try {
            fork.infer();
        } catch (Throwable ignored) {
        }
        return new StatelessInference(fork, userAst);
    }

    public static List<CompletionItem> completions(String preludeText, String userCode, int offset) {
        if (userCode == null) return List.of();
        offset = Math.max(0, Math.min(offset, userCode.length()));
        String parseUserCode = MetaExtractor.parseCodeForCompletion(userCode, offset);
        StatelessInference si = inferWithPrelude(preludeText, parseUserCode);
        if (si == null) return List.of();
        ModuleMeta outerScope = MetaExtractor.outerScopeFromPreamble(preambleAsf(preludeText));
        return MetaExtractor.completionsAt(si.asf(), si.ast(), userCode, offset, outerScope);
    }

    public static List<Map<String, Object>> completionsWire(String preludeText, String userCode, int offset) {
        List<Map<String, Object>> wire = new ArrayList<>();
        for (CompletionItem it : completions(preludeText, userCode, offset)) {
            wire.add(it.toMap());
        }
        return wire;
    }

    /**
     * Validate {@code userCode} against {@code preludeText}. Markers are in
     * {@code userCode} coordinates (no prelude line shift).
     */
    public static List<Map<String, Object>> validateMarkers(String preludeText, String userCode) {
        List<Map<String, Object>> markers = new ArrayList<>();
        try {
            StatelessInference si = inferWithPrelude(preludeText, userCode);
            if (si == null) {
                markers.add(marker(1, 0, 1, 100, "parse failed", 8));
            } else {
                collectInferenceMarkers(si.asf(), userCode, markers);
            }
        } catch (AggregateGrammarSyntaxException e) {
            for (GrammarSyntaxException ge : e.errors()) {
                markers.add(OutlineSyntaxDiagnostics.parseMarker(ge.getMessage(), userCode));
            }
        } catch (GrammarSyntaxException e) {
            markers.add(OutlineSyntaxDiagnostics.parseMarker(e.getMessage(), userCode));
        } catch (Exception e) {
            markers.add(marker(1, 0, 1, 100,
                    e.getMessage() != null ? e.getMessage() : "Unknown error", 8));
        }
        return OutlineSyntaxDiagnostics.normalizeMarkers(markers);
    }

    /**
     * Split combined wire code, validate the user suffix. Markers stay in
     * {@code userCode} coordinates (wrap segment); {@code outline-lang.js}
     * subtracts only {@code wrap.open} line count — never shift for prelude.
     */
    public static List<Map<String, Object>> validateCombinedWire(Map<String, Object> body, String code) {
        String validateCode = code != null && code.endsWith(".") ? code.substring(0, code.length() - 1) : code;
        String[] split = splitPreludeBody(body, validateCode);
        return validateMarkers(split[0], split[1]);
    }

    /**
     * Generic hover for a symbol at {@code offset} within {@code userCode}
     * (post-prelude strip, including wrap envelope when present).
     */
    public static Map<String, Object> hoverSymbol(String preludeText, String userCode, int offset) {
        if (userCode == null || userCode.isBlank()) return null;
        offset = Math.max(0, Math.min(offset, userCode.length()));
        int start = offset;
        while (start > 0 && (Character.isLetterOrDigit(userCode.charAt(start - 1)) || userCode.charAt(start - 1) == '_')) {
            start--;
        }
        int end = offset;
        while (end < userCode.length() && (Character.isLetterOrDigit(userCode.charAt(end)) || userCode.charAt(end) == '_')) {
            end++;
        }
        if (start >= end) return null;
        String word = userCode.substring(start, end);

        String inferUserCode = userCode.replaceAll("\\.([)\\]},])", "$1").replaceAll("\\.$", "");
        StatelessInference si = inferWithPrelude(preludeText, inferUserCode);
        if (si == null) return null;
        AST ast = si.ast();

        org.twelve.gcp.meta.SymbolMeta sym = MetaExtractor.resolveSymbolAt(ast, word, start);
        var envSym = ast.symbolEnv().lookupSymbol(word);
        String typeFromEnv = null;
        if (envSym != null && envSym.outline() != null) {
            String nominal = MetaExtractor.nominalTypeNameFromVisibleScopes(envSym.outline(), ast.symbolEnv());
            typeFromEnv = MetaExtractor.formatType(nominal != null ? nominal : envSym.outline().toString());
        }
        if (sym != null && sym.type() != null && !sym.type().isBlank()
                && !sym.type().equals(word) && !isSelfReferentialTypeLabel(sym.type(), word)) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("name", word);
            out.put("kind", sym.kind() != null ? sym.kind() : "variable");
            out.put("type", MetaExtractor.formatType(sym.type()));
            return out;
        }
        if (envSym != null && envSym.outline() != null) {
            String type = typeFromEnv;
            if (type != null && !type.isBlank() && !"?".equals(type)) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("name", word);
                out.put("kind", "variable");
                out.put("type", type);
                return out;
            }
        }
        return null;
    }

    private static boolean isSelfReferentialTypeLabel(String type, String name) {
        if (type == null || name == null) return false;
        String t = type.trim();
        return t.equals(name) || t.equals("`" + name + "`");
    }

    /**
     * Infer the return type of an expression or lambda in {@code userCode}
     * against {@code preludeText}. Used by {@code /api/infer} stateless path.
     *
     * <p>Decision-executor lambdas often infer as {@code Decision{payload: ?}} when
     * payload locals live inside nested scopes; this method recovers
     * {@code Decision<T>} from the tail {@code Decision\{ payload: … \}} literal.
     */
    public static String inferReturnType(String preludeText, String userCode) {
        if (userCode == null || userCode.isBlank()) return "Unit";
        String inferUserCode = userCode.replaceAll("\\.([)\\]},])", "$1").replaceAll("\\.$", "").trim();
        if (inferUserCode.isBlank()) return "Unit";
        if (inferUserCode.endsWith(";")) return "Unit";
        String expr = inferUserCode.replaceAll(";+$", "").trim();
        if (expr.isBlank()) return "Unit";

        String probeSource = "let __infer_probe__ = " + expr + ";";
        StatelessInference si = inferWithPrelude(preludeText, probeSource);
        if (si == null) return "?";
        var sym = si.ast().symbolEnv().lookupSymbol("__infer_probe__");
        if (sym == null || sym.outline() == null) return "?";

        org.twelve.gcp.outline.Outline ret = peelFunctionReturn(sym.outline());
        String formatted = formatInferredOutline(ret, si);

        String decision = recoverDecisionReturnType(preludeText, inferUserCode, si, formatted);
        if (decision != null && !decision.isBlank() && !"?".equals(decision)) {
            return decision;
        }
        if (formatted != null && formatted.matches("Decision\\s*<[^>?]+>")) {
            return formatted;
        }
        if (formatted != null && looksLikeStructuralDecisionOutline(formatted)) {
            formatted = null;
        }
        if (formatted == null || formatted.isBlank() || "?".equals(formatted)
                || formatted.contains("Error")
                || (formatted.contains("Decision") && formatted.contains("?"))) {
            return "?";
        }
        return formatted;
    }

    private static org.twelve.gcp.outline.Outline peelFunctionReturn(org.twelve.gcp.outline.Outline outline) {
        if (outline == null) return null;
        org.twelve.gcp.outline.Outline cur = outline;
        for (int i = 0; i < 16; i++) {
            org.twelve.gcp.outline.Outline ev = cur.eventual();
            if (ev instanceof Function<?, ?> fn) {
                org.twelve.gcp.outline.Outline next = fn.returns().supposedToBe();
                if (next == null || next == cur) return cur;
                cur = next;
            } else {
                return ev;
            }
        }
        return cur;
    }

    private static String formatInferredOutline(org.twelve.gcp.outline.Outline outline, StatelessInference si) {
        if (outline == null) return "?";
        String nominal = MetaExtractor.nominalTypeNameFromVisibleScopes(outline, si.ast().symbolEnv());
        if (nominal != null && !nominal.isBlank() && !"?".equals(nominal)) {
            return MetaExtractor.formatType(nominal);
        }
        return MetaExtractor.formatType(outline.toString());
    }

    /**
     * When GCP collapses a {@code Decision\{ payload: x \}} tail to
     * {@code Decision\{payload: ?\}} / {@code Error}, recover {@code Decision<T>}
     * from the payload binding.
     */
    private static String recoverDecisionReturnType(String preludeText, String userCode,
                                                    StatelessInference si, String current) {
        if (current != null && current.matches("Decision\\s*<[^>?]+>")) {
            return MetaExtractor.formatType(current);
        }
        String payloadIdent = extractDecisionPayloadIdent(userCode);
        if (payloadIdent == null || payloadIdent.isBlank()) return null;

        String payloadType = inferLocalAssignmentType(preludeText, userCode, payloadIdent);
        if (!isUsefulPayloadType(payloadType)) {
            int off = userCode.lastIndexOf(payloadIdent);
            if (off >= 0) {
                var sym = MetaExtractor.resolveSymbolAt(si.ast(), payloadIdent, off);
                if (sym != null && sym.type() != null && isUsefulPayloadType(sym.type())) {
                    payloadType = MetaExtractor.formatType(sym.type());
                }
            }
        }
        if (!isUsefulPayloadType(payloadType)) return null;
        payloadType = payloadType.replace(" → ", "->").replace("?", "").trim();
        if (payloadType.toLowerCase().contains("epsilon")) return null;
        return "Decision<" + payloadType + ">";
    }

    private static boolean isUsefulPayloadType(String type) {
        if (type == null || type.isBlank()) return false;
        String t = type.trim();
        return !"?".equals(t) && !t.contains("Error") && !t.equalsIgnoreCase("Unknown")
                && !t.toLowerCase().contains("epsilon");
    }

    /** GCP sometimes expands {@code Decision\{payload: T\}} to a structural row type. */
    private static boolean looksLikeStructuralDecisionOutline(String formatted) {
        if (formatted == null) return false;
        String s = formatted.trim();
        return s.startsWith("{") && s.contains("payload:");
    }

    private static String extractDecisionPayloadIdent(String userCode) {
        if (userCode == null) return null;
        Matcher m = Pattern.compile("Decision\\s*\\{[^}]*?payload\\s*:\\s*([A-Za-z_][A-Za-z0-9_]*)")
                .matcher(userCode);
        String found = null;
        while (m.find()) found = m.group(1);
        return found;
    }

    /** Infer {@code let name = expr;} type using prefix context (lambda params in scope). */
    static String inferLocalAssignmentType(String preludeText, String userCode, String localName) {
        if (userCode == null || localName == null || localName.isBlank()) return null;
        Matcher m = Pattern.compile("(?s)(.*)\\blet\\s+" + Pattern.quote(localName)
                + "\\b\\s*=\\s*(.*?);").matcher(userCode);
        String prefix = null;
        String expr = null;
        while (m.find()) {
            prefix = m.group(1);
            expr = m.group(2);
        }
        if (expr == null || expr.isBlank()) return null;
        String exprTrim = expr.trim();

        MethodCallExpr call = parseMethodCallExpr(exprTrim);
        if (call != null) {
            String fromMethod = inferMethodCallReturnType(preludeText, prefix, call);
            if (isUsefulPayloadType(fromMethod)) return fromMethod;
        }

        String probeUserCode = (prefix == null ? "" : prefix) + "\nlet __infer_assign__ = " + exprTrim + ";";
        StatelessInference probeSi = inferWithPrelude(preludeText == null ? "" : preludeText, probeUserCode);
        if (probeSi == null) return null;
        var sym = probeSi.ast().symbolEnv().lookupSymbol("__infer_assign__");
        if (sym == null || sym.outline() == null) return null;
        String nominal = MetaExtractor.nominalTypeNameFromVisibleScopes(sym.outline(), probeSi.ast().symbolEnv());
        return MetaExtractor.formatType(nominal != null ? nominal : sym.outline().toString());
    }

    private static String inferMethodCallReturnType(String preludeText, String prefix, MethodCallExpr call) {
        String recvProbe = (prefix == null ? "" : prefix) + "\nlet __infer_recv__ = " + call.receiverExpr + ";";
        StatelessInference si = inferWithPrelude(preludeText == null ? "" : preludeText, recvProbe);
        if (si == null) return null;
        var recv = si.ast().symbolEnv().lookupSymbol("__infer_recv__");
        if (recv == null || recv.outline() == null) return null;
        ModuleMeta outer = MetaExtractor.outerScopeFromPreamble(preambleAsf(preludeText));
        Outline recvOutline = recv.outline();
        if (outer != null && call.receiverExpr.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            var fields = MetaExtractor.completionMembersOfMethodReturn(
                    call.receiverExpr, call.methodName, outer);
            for (var f : fields) {
                if (call.methodName.equals(f.name()) || (call.methodName + "()").equals(f.name())) {
                    String t = f.type();
                    int arrow = Math.max(t.lastIndexOf("->"), t.lastIndexOf("→"));
                    if (arrow >= 0 && arrow + 2 < t.length()) {
                        return MetaExtractor.formatType(t.substring(arrow + (t.charAt(arrow) == '→' ? 1 : 2)).trim());
                    }
                }
            }
        }
        return MetaExtractor.formatType(
                MetaExtractor.methodReturnTypeOf(recvOutline, call.methodName, si.asf(), call.argumentCount));
    }

    private record MethodCallExpr(String receiverExpr, String methodName, int argumentCount) {}

    private static MethodCallExpr parseMethodCallExpr(String expr) {
        if (expr == null) return null;
        String s = expr.trim();
        if (s.isBlank() || !s.endsWith(")")) return null;
        int close = s.length() - 1;
        int depth = 0;
        int open = -1;
        for (int i = close; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == ')') depth++;
            else if (c == '(') {
                depth--;
                if (depth == 0) {
                    open = i;
                    break;
                }
            }
        }
        if (open <= 0) return null;
        String before = s.substring(0, open).trim();
        int dot = before.lastIndexOf('.');
        if (dot <= 0 || dot >= before.length() - 1) return null;
        String receiver = before.substring(0, dot).trim();
        String method = before.substring(dot + 1).trim();
        if (receiver.isBlank() || method.isBlank()) return null;
        if (!method.matches("[A-Za-z_][A-Za-z0-9_]*")) return null;
        String args = s.substring(open + 1, close).trim();
        return new MethodCallExpr(receiver, method, countTopLevelArgs(args));
    }

    private static int countTopLevelArgs(String args) {
        if (args == null || args.isBlank()) return 0;
        int count = 1;
        int depth = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '(' || c == '{' || c == '[') depth++;
            else if (c == ')' || c == '}' || c == ']') depth--;
            else if (c == ',' && depth == 0) count++;
        }
        return count;
    }

    private static void collectInferenceMarkers(ASF asf, String code, List<Map<String, Object>> markers) {
        if (!asf.asts().isEmpty()) {
            for (String syntaxMsg : asf.asts().get(0).syntaxErrors()) {
                markers.add(OutlineSyntaxDiagnostics.parseMarker(syntaxMsg, code));
            }
        }
        for (GCPError e : asf.allErrors()) {
            try {
                var loc = e.node().loc();
                int startLine = Math.max(1, loc.line());
                int startCol = Math.max(0, loc.col());
                int endLine = startLine;
                int endCol = startCol + (e.node().lexeme() != null ? e.node().lexeme().length() : 5);
                if (startLine == 1 && startCol == 0 && loc.start() >= 0) {
                    int startOffset = (int) Math.max(0, loc.start());
                    int endOffset = (int) Math.max(startOffset + 1, loc.end() + 1);
                    if (startOffset <= code.length()) {
                        int[] startLc = offsetToLineCol(code, startOffset);
                        int[] endLc = offsetToLineCol(code, Math.min(endOffset, code.length()));
                        startLine = startLc[0];
                        startCol = startLc[1];
                        endLine = endLc[0];
                        endCol = endLc[1];
                        if (endLine == startLine && endCol <= startCol) {
                            endCol = startCol + 1;
                        }
                    }
                }
                int sev = "warning".equals(e.severity().wireValue()) ? 4 : 8;
                markers.add(marker(startLine, startCol, endLine, endCol, e.displayMessage(), sev));
            } catch (Throwable ignored) {
                markers.add(marker(1, 0, 1, 100, e.displayMessage(), 8));
            }
        }
    }

    private static Map<String, Object> marker(int sl, int sc, int el, int ec, String msg, int sev) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("startLine", sl);
        m.put("startColumn", sc);
        m.put("endLine", el);
        m.put("endColumn", ec);
        m.put("message", msg);
        m.put("severity", sev);
        return m;
    }

    private static int[] offsetToLineCol(String code, int offset) {
        if (code == null || offset <= 0) return new int[] { 1, 0 };
        int line = 1;
        int lineStart = 0;
        int limit = Math.min(offset, code.length());
        for (int i = 0; i < limit; i++) {
            if (code.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        return new int[] { line, limit - lineStart };
    }

    private static String preludeKey(String preludeText) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1")
                    .digest(preludeText.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(preludeText.hashCode());
        }
    }
}
