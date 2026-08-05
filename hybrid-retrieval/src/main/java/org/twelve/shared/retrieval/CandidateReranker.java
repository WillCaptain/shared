package org.twelve.shared.retrieval;

import java.util.List;

/** Optional final-stage reranker. Implementations must return at most {@code limit} hits. */
@FunctionalInterface
public interface CandidateReranker {
    List<RetrievalHit> rerank(RetrievalQuery query, List<RetrievalHit> candidates, int limit);

    static CandidateReranker identity() {
        return (query, candidates, limit) ->
                candidates.size() <= limit ? List.copyOf(candidates) : List.copyOf(candidates.subList(0, limit));
    }
}
