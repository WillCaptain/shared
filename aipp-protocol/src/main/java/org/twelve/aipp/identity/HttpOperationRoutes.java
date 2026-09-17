package org.twelve.aipp.identity;

import java.net.URI;
import java.util.*;

/** Immutable per-app routing snapshot. Matching is not authentication or a resource grant. */
public final class HttpOperationRoutes {
    public static final String FIELD = "http_routes";
    public static final String VERSION = "operation-routes-v1";
    private static final Set<String> METHODS = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE");
    private static final String SEGMENT = "[A-Za-z0-9_-][A-Za-z0-9_.-]{0,127}";
    public record Operation(String name, boolean requiresIdentity) {}
    private record Route(String method, List<String> segments, Operation operation) {}
    private final List<Route> routes;

    private HttpOperationRoutes(List<Route> routes) { this.routes = List.copyOf(routes); }

    /** Compile one app's trusted catalog, rejecting overlaps before any request can be sent. */
    public static HttpOperationRoutes compile(List<? extends Map<String, ?>> tools) {
        if (tools == null || tools.size() > 4096) throw invalid();
        List<Route> routes = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Map<String, ?> tool : tools) {
            if (tool == null) throw invalid();
            // Even a non-routed duplicate can make the eventual function lookup ambiguous.
            Object name = tool.get("name");
            if (!(name instanceof String operation) || !names.add(operation)) throw invalid();
            if (!tool.containsKey(FIELD)) continue;
            if (!operation.matches("[a-z][a-z0-9_]{0,127}")) throw invalid();
            if (!(tool.get(FIELD) instanceof Map<?, ?> declaration)
                    || !declaration.keySet().equals(Set.of("version", "routes"))
                    || !VERSION.equals(declaration.get("version"))
                    || !(declaration.get("routes") instanceof List<?> entries)
                    || entries.isEmpty() || entries.size() > 32) throw invalid();
            boolean protectedRoute = tool.containsKey(AippInvocationIdentityContract.REQUIREMENT_FIELD);
            if (protectedRoute && !AippInvocationIdentityContract.VERIFIED_REQUEST_V1.equals(
                    tool.get(AippInvocationIdentityContract.REQUIREMENT_FIELD))) throw invalid();
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> route) || !route.keySet().equals(Set.of("method", "path"))
                        || !(route.get("method") instanceof String method) || !METHODS.contains(method)
                        || !(route.get("path") instanceof String path)) throw invalid();
                List<String> segments = segments(path, true);
                var next = new Route(method, segments, new Operation(operation, protectedRoute));
                for (Route previous : routes) {
                    if (overlaps(previous, next)) throw invalid();
                }
                if (routes.size() >= 256) throw invalid();
                routes.add(next);
            }
        }
        return new HttpOperationRoutes(routes);
    }

    /** Match the raw target without decoding, normalizing, or reserializing its query. */
    public Optional<Operation> resolve(String method, String requestTarget) {
        if (!METHODS.contains(Objects.toString(method, "")) || requestTarget == null
                || requestTarget.length() > 4096
                || !requestTarget.chars().allMatch(c -> c >= 33 && c <= 126)) throw invalid();
        URI uri;
        try { uri = URI.create(requestTarget); }
        catch (IllegalArgumentException bad) { throw invalid(); }
        if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawFragment() != null) throw invalid();
        List<String> requested = segments(uri.getRawPath(), false);
        for (Route route : routes) {
            if (!route.method().equals(method) || route.segments().size() != requested.size()) continue;
            boolean match = true;
            for (int i = 0; i < requested.size(); i++) {
                String part = route.segments().get(i);
                if (!parameter(part) && !part.equals(requested.get(i))) { match = false; break; }
            }
            if (match) return Optional.of(route.operation());
        }
        return Optional.empty();
    }

    private static List<String> segments(String path, boolean template) {
        if (path == null || path.length() > 1024 || !path.startsWith("/api/")) throw invalid();
        String[] parts = path.substring(1).split("/", -1);
        if (parts.length < 2 || parts.length > 16) throw invalid();
        Set<String> parameters = new HashSet<>();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (template && parameter(part)) {
                // Keep the API namespace literal; aliases cannot shadow /api/tools/*.
                if (i < 2 || !parameters.add(part)) throw invalid();
            } else if (!part.matches(SEGMENT)) throw invalid();
        }
        if (Set.of("tools", "proxy").contains(parts[1])) throw invalid();
        return List.of(parts);
    }

    private static boolean parameter(String part) { return part.matches("\\{[A-Za-z][A-Za-z0-9_]{0,63}\\}"); }

    private static boolean overlaps(Route left, Route right) {
        if (!left.method().equals(right.method()) || left.segments().size() != right.segments().size()) return false;
        for (int i = 0; i < left.segments().size(); i++) {
            String a = left.segments().get(i), b = right.segments().get(i);
            if (!parameter(a) && !parameter(b) && !a.equals(b)) return false;
        }
        return true;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid or ambiguous HTTP operation routes");
    }
}
