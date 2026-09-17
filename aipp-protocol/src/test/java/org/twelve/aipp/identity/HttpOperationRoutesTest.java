package org.twelve.aipp.identity;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HttpOperationRoutesTest {
    @Test void sharedManifestValidatorRejectsCrossOperationCollision() {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var spec = new org.twelve.aipp.AippAppSpec();
        var tools = List.of(tool("read", "GET", "/api/items/{id}"), tool("other", "GET", "/api/items/fixed"));
        assertThrows(AssertionError.class, () -> spec.assertValidHttpOperationRoutes(mapper.valueToTree(tools)));
        assertDoesNotThrow(() -> spec.assertValidHttpOperationRoutes(mapper.valueToTree(
                List.of(tool("read", "GET", "/api/items/{id}"), Map.of("name", "legacy-detail")))));
    }
    static Map<String, Object> tool(String name, String method, String path) {
        return Map.of("name", name, HttpOperationRoutes.FIELD, Map.of("version", HttpOperationRoutes.VERSION,
                "routes", List.of(Map.of("method", method, "path", path))));
    }

    @Test void matchesOnlyExactMethodAndSingleBoundedSegment() {
        var table = HttpOperationRoutes.compile(List.of(tool("rename", "PATCH", "/api/documents/{id}/rename")));
        assertEquals("rename", table.resolve("PATCH", "/api/documents/abc-123/rename?env=staging&x=%E4%B8%AD").orElseThrow().name());
        assertTrue(table.resolve("GET", "/api/documents/abc-123/rename").isEmpty());
        assertTrue(table.resolve("PATCH", "/api/documents/abc-123/extra/rename").isEmpty());
        assertTrue(table.resolve("PATCH", "/api/other/abc-123/rename").isEmpty());
    }

    @Test void legacyAndProtectedMetadataRemainDistinctAndSnapshotIsImmutable() {
        var item = new HashMap<>(tool("review", "POST", "/api/reviews/{id}"));
        item.put("invocation_identity", "verified-request-v1");
        var table = HttpOperationRoutes.compile(List.of(item));
        item.put("name", "replaced"); item.remove("http_routes");
        assertEquals(new HttpOperationRoutes.Operation("review", true), table.resolve("POST", "/api/reviews/123").orElseThrow());
        assertFalse(HttpOperationRoutes.compile(List.of(tool("read", "GET", "/api/info")))
                .resolve("GET", "/api/info").orElseThrow().requiresIdentity());
        assertTrue(HttpOperationRoutes.compile(List.of(Map.of("name", "ordinary"))).resolve("GET", "/api/info").isEmpty());
    }

    @Test void overlappingPatternsFailEvenIfLiteralIsMoreSpecificOrOperationIsSame() {
        var generic = tool("read", "GET", "/api/documents/{id}");
        for (var second : List.of(tool("other", "GET", "/api/documents/fixed"),
                tool("other", "GET", "/api/documents/{another}"), generic))
            assertThrows(IllegalArgumentException.class, () -> HttpOperationRoutes.compile(List.of(generic, second)));
        assertDoesNotThrow(() -> HttpOperationRoutes.compile(List.of(generic, tool("write", "PATCH", "/api/documents/{id}"))));
    }

    @Test void malformedTargetsAndWildcardDeclarationsFailClosed() {
        for (String path : List.of("/api/documents/../info", "/api/documents/%2e%2e/info", "/api/documents/a%2Fb",
                "/api/documents/a;b", "/api/documents/a//b", "/api/documents/a/", "/api/documents/*",
                "https://example.com/api/info", "//example.com/api/info", "/api/info#x", "/api/中文")) {
            assertThrows(IllegalArgumentException.class, () -> HttpOperationRoutes.compile(List.of(tool("read", "GET", path))));
            assertThrows(IllegalArgumentException.class, () -> HttpOperationRoutes.compile(List.of()).resolve("GET", path));
        }
        for (String path : List.of("/api/{namespace}/item", "/api/tools/{name}", "/api/proxy/x", "/api/info?q=1",
                "/api/items/{id}/{id}"))
            assertThrows(IllegalArgumentException.class, () -> HttpOperationRoutes.compile(List.of(tool("read", "GET", path))));
    }

    @Test void unknownVersionNullRequirementAndExtraFieldsAreRejected() {
        for (Object value : Arrays.asList(null, "v1", Map.of("version", "future", "routes", List.of()),
                Map.of("version", HttpOperationRoutes.VERSION, "routes", List.of(), "allow_all", true))) {
            var item = new HashMap<String, Object>(); item.put("name", "read"); item.put("http_routes", value);
            assertThrows(IllegalArgumentException.class, () -> HttpOperationRoutes.compile(List.of(item)));
        }
        var item = new HashMap<>(tool("read", "GET", "/api/info"));
        for (Object requirement : Arrays.asList(null, "future", true)) {
            item.put("invocation_identity", requirement);
            assertThrows(IllegalArgumentException.class, () -> HttpOperationRoutes.compile(List.of(item)));
        }
        assertThrows(IllegalArgumentException.class, () -> HttpOperationRoutes.compile(List.of(tool("read", "get", "/api/info"))));
    }

    @Test void catalogsAndTargetsAreBounded() {
        var routes = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 257; i++) routes.add(tool("read_" + i, "GET", "/api/info_" + i));
        assertThrows(IllegalArgumentException.class, () -> HttpOperationRoutes.compile(routes));
        assertThrows(IllegalArgumentException.class, () -> HttpOperationRoutes.compile(List.of()).resolve("GET", "/api/info?q=" + "a".repeat(4096)));
        assertThrows(IllegalArgumentException.class, () -> HttpOperationRoutes.compile(List.of()).resolve("GET", "/api/items/" + "a".repeat(129)));
    }
}
