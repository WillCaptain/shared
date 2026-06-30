package org.twelve.aipp.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads {@code host_access_token} from {@code ~/.ones/host.json}.
 *
 * <p>See {@code spec/llm-config.md} §7.3 and {@code spec/host-url.md}.
 */
public final class HostAccessToken {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HostAccessToken() {}

    public static String load() {
        Path path = HostUrlResolver.globalConfigPath();
        if (!Files.isRegularFile(path)) return "";
        try {
            JsonNode root = JSON.readTree(path.toFile());
            return root.path("host_access_token").asText("").trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    /** {@code Authorization: Bearer …} header value, or empty when absent. */
    public static String fromAuthorizationHeader(String header) {
        if (header == null || header.isBlank()) return "";
        String h = header.trim();
        if (h.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return h.substring(7).trim();
        }
        return "";
    }
}
