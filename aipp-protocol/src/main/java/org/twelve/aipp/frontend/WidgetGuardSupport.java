package org.twelve.aipp.frontend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reusable scaffolding for AIPP frontend guard tests. Each AIPP repo subclasses
 * the concrete {@code WidgetUrlResolvesTest} / {@code WidgetNoHostCouplingTest}
 * to plug in its own base URL + static dir.
 *
 * <p>This class is intentionally framework-free — no JUnit annotations — so
 * downstream test classes can be plain JUnit 5 in the AIPP repo.
 */
public final class WidgetGuardSupport {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();

    private WidgetGuardSupport() { }

    // ── Widget URL resolves ──────────────────────────────────────────────────

    public record UrlCheckResult(boolean skipped, List<String> failures) { }

    /** Returns failures (empty when all good). Skips if AIPP not reachable. */
    public static UrlCheckResult checkAllWidgetUrls(String baseUrl) {
        JsonNode widgets;
        try {
            widgets = httpJson(baseUrl + "/api/widgets");
        } catch (Exception e) {
            return new UrlCheckResult(true, List.of("AIPP not reachable at " + baseUrl + ": " + e.getMessage()));
        }
        if (widgets == null) return new UrlCheckResult(true, List.of("/api/widgets returned null"));
        return checkWidgetManifests(widgets.path("widgets"), baseUrl, null);
    }

    /**
     * Aggregated Host check: {@code GET {worldOneUrl}/api/widgets} returns every
     * registered AIPP widget (with {@code app_base_url}). Validates both the
     * direct AIPP URL and the Host proxy URL the browser uses for {@code import()}.
     */
    public static UrlCheckResult checkAllWidgetsViaWorldOne(String worldOneUrl) {
        JsonNode widgets;
        try {
            widgets = httpJson(worldOneUrl + "/api/widgets");
        } catch (Exception e) {
            return new UrlCheckResult(true, List.of("World One not reachable at " + worldOneUrl + ": " + e.getMessage()));
        }
        if (widgets == null) return new UrlCheckResult(true, List.of("/api/widgets returned null"));
        return checkWidgetManifests(widgets.path("widgets"), worldOneUrl, worldOneUrl);
    }

    private static UrlCheckResult checkWidgetManifests(JsonNode widgets, String defaultBaseUrl, String worldOneUrl) {
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (JsonNode w : widgets) {
            String renderUrl = w.path("render").path("url").asText("");
            if (renderUrl.isBlank()) continue;
            String appId = w.path("app_id").asText("");
            String widgetType = w.path("type").asText("?");
            String appBase = w.path("app_base_url").asText(defaultBaseUrl);
            if (appBase.isBlank()) appBase = defaultBaseUrl;
            String kind = w.path("render").path("kind").asText("");

            String direct = renderUrl.startsWith("http") ? renderUrl : appBase + renderUrl;
            failures.addAll(fetchAndValidate(direct, kind, appId + "/" + widgetType + " (direct)"));
            checked++;

            if (worldOneUrl != null && !appId.isBlank()) {
                String path = renderUrl.startsWith("http")
                        ? URI.create(renderUrl).getPath()
                        : (renderUrl.startsWith("/") ? renderUrl : "/" + renderUrl);
                String proxy = worldOneUrl + "/api/proxy/app/" + encodeAppId(appId) + path;
                failures.addAll(fetchAndValidate(proxy, kind, appId + "/" + widgetType + " (host-proxy)"));
            }
        }
        if (checked == 0) {
            return new UrlCheckResult(true, List.of("no widgets with render.url in manifest"));
        }
        return new UrlCheckResult(false, failures);
    }

    private static String encodeAppId(String appId) {
        return URI.create("http://x/" + appId).getRawPath().substring(1);
    }

    private static List<String> fetchAndValidate(String fullUrl, String kind, String label) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(fullUrl))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return List.of(label + " " + fullUrl + " → HTTP " + resp.statusCode());
            }
            return validateRenderedBody(resp.body(), kind, label + " " + fullUrl);
        } catch (Exception e) {
            return List.of(label + " " + fullUrl + " → " + e.getMessage());
        }
    }

    private static List<String> validateRenderedBody(String body, String kind, String label) {
        if (body == null || body.isBlank()) {
            return List.of(label + " → empty body");
        }
        if ("esm".equals(kind)) {
            Optional<String> esmErr = validateEsmWidgetSource(body);
            if (esmErr.isPresent()) {
                return List.of(label + " → " + esmErr.get());
            }
            String lower = body.stripLeading().toLowerCase(Locale.ROOT);
            if (!lower.contains("export function mount") && !lower.contains("export async function mount")) {
                return List.of(label + " → ESM module missing export function mount");
            }
            return List.of();
        }
        String lower = body.stripLeading().toLowerCase(Locale.ROOT);
        boolean looksHtml = lower.startsWith("<!doctype html") || lower.startsWith("<html");
        if (!looksHtml) {
            return List.of(label + " → body does not look like HTML (first 60 chars: '"
                    + body.substring(0, Math.min(60, body.length())) + "')");
        }
        return List.of();
    }

    // ── ESM widget source validation (static, no server) ─────────────────────

    private static final Pattern MOUNT_EXPORT = Pattern.compile(
            "export\\s+(async\\s+)?function\\s+mount\\s*\\(",
            Pattern.MULTILINE);

    /**
     * Validates widget ESM source before deploy. Catches top-level CSS pasted outside
     * template literals (breaks {@code import()} — see entity-graph regression).
     */
    public static Optional<String> validateEsmWidgetSource(String source) {
        if (source == null || source.isBlank()) {
            return Optional.of("empty source");
        }
        if (!MOUNT_EXPORT.matcher(source).find()) {
            return Optional.of("missing export function mount");
        }
        return findBareCssOutsideStrings(source);
    }

    /** Walk {@code static/widgets/} tree and validate ESM syntax/loadability. */
    public static List<String> scanEsmWidgetsOnDisk(Path widgetsRoot) {
        if (!Files.isDirectory(widgetsRoot)) return List.of();
        List<String> failures = new ArrayList<>();
        try (Stream<Path> files = Files.walk(widgetsRoot)) {
            files.filter(Files::isRegularFile)
                 .filter(p -> {
                     String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                     return n.endsWith(".js") || n.endsWith(".mjs");
                 })
                 .forEach(p -> {
                     String src;
                     try { src = Files.readString(p); }
                     catch (Exception e) {
                         failures.add(widgetsRoot.relativize(p) + " → read failed: " + e.getMessage());
                         return;
                     }
                     findBareCssOutsideStrings(src).ifPresent(err ->
                             failures.add(widgetsRoot.relativize(p) + " → " + err));
                     if (MOUNT_EXPORT.matcher(src).find()) {
                         validateEsmWidgetSource(src).ifPresent(err ->
                                 failures.add(widgetsRoot.relativize(p) + " → " + err));
                     }
                 });
        } catch (Exception e) {
            failures.add("scan error: " + e.getMessage());
        }
        return failures;
    }

    static Optional<String> findBareCssOutsideStrings(String src) {
        boolean inSingle = false, inDouble = false, inTemplate = false;
        boolean inLineComment = false, inBlockComment = false;
        int line = 1;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            char next = i + 1 < src.length() ? src.charAt(i + 1) : 0;

            if (inLineComment) {
                if (c == '\n') { inLineComment = false; line++; }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') { inBlockComment = false; i++; }
                else if (c == '\n') line++;
                continue;
            }
            if (!inSingle && !inDouble && !inTemplate) {
                if (c == '/' && next == '/') { inLineComment = true; i++; continue; }
                if (c == '/' && next == '*') { inBlockComment = true; i++; continue; }
            }
            if (!inDouble && !inTemplate && c == '\'' && !isEscaped(src, i)) inSingle = !inSingle;
            else if (!inSingle && !inTemplate && c == '"' && !isEscaped(src, i)) inDouble = !inDouble;
            else if (!inSingle && !inDouble && c == '`' && !isEscaped(src, i)) inTemplate = !inTemplate;

            if (!inSingle && !inDouble && !inTemplate) {
                if (c == '.' || c == '#') {
                    int lineStart = src.lastIndexOf('\n', i - 1) + 1;
                    if (src.substring(lineStart, i).isBlank()) {
                        int end = src.indexOf('\n', i);
                        String rest = end < 0 ? src.substring(i) : src.substring(i, end);
                        String trimmed = rest.trim();
                        if (trimmed.matches("^\\.[\\w-]+\\s*\\{.*") || trimmed.matches("^#[\\w-]+\\s*\\{.*")) {
                            return Optional.of("bare CSS outside string at line " + line + ": " + trimmed);
                        }
                    }
                }
            }
            if (c == '\n') line++;
        }
        return Optional.empty();
    }

    private static boolean isEscaped(String src, int idx) {
        int slashes = 0;
        for (int j = idx - 1; j >= 0 && src.charAt(j) == '\\'; j--) slashes++;
        return slashes % 2 == 1;
    }

    // ── Widget host-coupling scan ────────────────────────────────────────────

    /**
     * Forbidden patterns inside any widget asset:
     * <ol>
     *   <li>{@code parent.X}</li>
     *   <li>{@code window.top} access</li>
     *   <li>{@code top.X} access</li>
     *   <li>fetch / XHR to a path under {@code /api/} that is NOT prefixed with
     *       this AIPP's own base URL.  Heuristic: any string literal {@code "/api/...
     *       or 'api/...'} is suspect; route app calls through {@code hostApi.appProxyUrl(path)}
     *       or {@code hostApi.proxyTool(name, args)}.</li>
     * </ol>
     */
    public static List<String> scanWidgetCoupling(Path widgetsRoot) {
        if (!Files.isDirectory(widgetsRoot)) return List.of();
        List<String> hits = new ArrayList<>();

        Pattern parentBad = Pattern.compile("\\bparent\\s*\\.\\s*([A-Za-z_$][\\w$]*)");
        Pattern windowTop = Pattern.compile("\\bwindow\\s*\\.\\s*top\\b");
        Pattern topAccess = Pattern.compile("(?<![A-Za-z_$.])top\\s*\\.(?!toString\\b)");
        Pattern apiPath   = Pattern.compile("[\"'`]/api/[A-Za-z0-9_./{}-]+[\"'`]");

        try (Stream<Path> files = Files.walk(widgetsRoot)) {
            files.filter(Files::isRegularFile)
                 .filter(p -> {
                     String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                     return n.endsWith(".html") || n.endsWith(".js") || n.endsWith(".mjs");
                 })
                 .forEach(p -> {
                     String src;
                     try { src = Files.readString(p); }
                     catch (Exception e) { return; }

                     scanFor(p, src, parentBad,
                            m -> "uses `parent." + m.group(1) + "` (use hostApi instead)",
                             hits, widgetsRoot);
                     scanFor(p, src, windowTop,
                             m -> "uses `window.top` (host-coupling forbidden)",
                             hits, widgetsRoot);
                     scanFor(p, src, topAccess,
                             m -> "uses `top.<x>` (host-coupling forbidden)",
                             hits, widgetsRoot);
                    // Bare /api/ string — likely a host call. Widgets should go
                    // through hostApi so the Host can proxy the owning AIPP.
                     scanFor(p, src, apiPath,
                            m -> "contains bare host-style API path `" + m.group() + "`; route via hostApi.appProxyUrl/proxyTool instead",
                             hits, widgetsRoot);
                 });
        } catch (Exception e) {
            hits.add("scan error: " + e.getMessage());
        }
        return hits;
    }

    // ── Widget local CSS scan (widgets.md §4) ────────────────────────────────

    private static final List<Pattern> LOCAL_CSS_PATTERNS = List.of(
            Pattern.compile("\\bensureStyles\\s*\\("),
            Pattern.compile("const\\s+STYLE_ID\\s*="),
            Pattern.compile("const\\s+CSS\\s*=\\s*`"),
            Pattern.compile("createElement\\(\\s*['\"]style['\"]\\s*\\)"));

    /**
     * Forbidden: widgets shipping injected {@code <style>} or inline CSS blocks
     * ({@code widgets.md} §4). Use shared {@code aipp-*} classes from Host-loaded CSS.
     */
    public static List<String> scanWidgetLocalCss(Path widgetsRoot) {
        if (!Files.isDirectory(widgetsRoot)) return List.of();
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(widgetsRoot)) {
            files.filter(Files::isRegularFile)
                 .filter(p -> {
                     String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                     return n.endsWith(".js") || n.endsWith(".mjs");
                 })
                 .forEach(p -> {
                     String src;
                     try { src = Files.readString(p); }
                     catch (Exception e) { return; }
                     for (Pattern pat : LOCAL_CSS_PATTERNS) {
                         scanFor(p, src, pat,
                                 m -> "ships local CSS (`" + m.group().trim() + "`); use shared aipp-* classes",
                                 hits, widgetsRoot);
                     }
                 });
        } catch (Exception e) {
            hits.add("scan error: " + e.getMessage());
        }
        return hits;
    }

    private static void scanFor(Path file, String src, Pattern p,
                                java.util.function.Function<Matcher, String> msg,
                                List<String> out, Path root) {
        Matcher m = p.matcher(src);
        while (m.find()) {
            int line = lineOf(src, m.start());
            out.add(root.relativize(file) + ":" + line + " — " + msg.apply(m));
        }
    }

    private static int lineOf(String src, int idx) {
        int line = 1;
        for (int i = 0; i < idx && i < src.length(); i++) {
            if (src.charAt(i) == '\n') line++;
        }
        return line;
    }

    private static JsonNode httpJson(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(2)).GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return null;
        return JSON.readTree(resp.body());
    }
}
