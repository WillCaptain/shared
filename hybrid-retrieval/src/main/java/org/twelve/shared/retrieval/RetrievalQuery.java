package org.twelve.shared.retrieval;

/** Generic retrieval request. A blank kind searches all document kinds. */
public record RetrievalQuery(
        String namespace,
        String text,
        String kind,
        int limit,
        int candidateLimit) {

    public RetrievalQuery {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }
        namespace = namespace.trim();
        text = text == null ? "" : text.trim();
        kind = kind == null ? "" : kind.trim();
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        if (candidateLimit < limit) {
            throw new IllegalArgumentException("candidateLimit must be at least limit");
        }
    }

    public RetrievalQuery(String namespace, String text, int limit) {
        this(namespace, text, "", limit, Math.max(limit, limit * 3));
    }
}
