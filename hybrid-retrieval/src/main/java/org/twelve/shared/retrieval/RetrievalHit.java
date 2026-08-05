package org.twelve.shared.retrieval;

import java.util.Set;

/** A fused retrieval result with optional per-channel scores. */
public record RetrievalHit(
        RetrievalDocument document,
        double score,
        Double lexicalScore,
        Double vectorScore,
        Set<String> sources) {

    public RetrievalHit {
        if (document == null) throw new IllegalArgumentException("document is required");
        sources = sources == null ? Set.of() : Set.copyOf(sources);
    }

    public RetrievalHit withScore(double newScore) {
        return new RetrievalHit(document, newScore, lexicalScore, vectorScore, sources);
    }
}
