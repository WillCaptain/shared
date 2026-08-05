package org.twelve.shared.retrieval;

import java.util.Map;
import java.util.Objects;

/** A caller-owned document represented without application-specific types. */
public record RetrievalDocument(
        String namespace,
        String id,
        String path,
        String kind,
        String title,
        String body,
        String contentHash,
        Map<String, Object> metadata) {

    public RetrievalDocument {
        namespace = require(namespace, "namespace");
        id = require(id, "id");
        path = path == null ? "" : path;
        kind = kind == null || kind.isBlank() ? "document" : kind.trim();
        title = title == null ? "" : title;
        body = body == null ? "" : body;
        contentHash = contentHash == null ? "" : contentHash;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String embeddingText() {
        return String.join("\n", title, path, body).trim();
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return Objects.requireNonNull(value).trim();
    }
}
