package org.twelve.shared.retrieval;

import org.twelve.shared.dbops.AtomicDbOps;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Coordinates indexing, lexical recall, dense recall, RRF, and optional reranking. */
public final class HybridRetrievalEngine {
    private final AtomicDbOps db;
    private final RetrievalSchema schema;
    private final LexicalRetrievalStore lexical;
    private final VectorRetrievalStore vectors;
    private final EmbeddingProvider embeddings;
    private final CandidateReranker reranker;

    public record IndexResult(int lexicalIndexed, int vectorIndexed, boolean embeddingsAvailable) {}

    public HybridRetrievalEngine(
            AtomicDbOps db,
            RetrievalSchema schema,
            EmbeddingProvider embeddings,
            CandidateReranker reranker) {
        this.db = db;
        this.schema = schema;
        this.lexical = new LexicalRetrievalStore(db, schema);
        this.vectors = new VectorRetrievalStore(db, schema);
        this.embeddings = embeddings;
        this.reranker = reranker == null ? CandidateReranker.identity() : reranker;
    }

    public HybridRetrievalEngine(
            AtomicDbOps db, RetrievalSchema schema, EmbeddingProvider embeddings) {
        this(db, schema, embeddings, CandidateReranker.identity());
    }

    public void initialize() {
        RetrievalDdl.initialize(db, schema);
    }

    public IndexResult index(RetrievalDocument document) {
        return indexAll(List.of(document));
    }

    /**
     * Writes lexical rows first. An unavailable embedding service leaves those rows searchable and
     * is reported in the result instead of failing the indexing operation.
     */
    public IndexResult indexAll(List<RetrievalDocument> documents) {
        if (documents == null || documents.isEmpty()) return new IndexResult(0, 0, embeddings != null);
        documents.forEach(lexical::upsert);
        if (embeddings == null) return new IndexResult(documents.size(), 0, false);
        final List<float[]> generated;
        try {
            generated = embeddings.embed(documents.stream()
                    .map(RetrievalDocument::embeddingText).toList());
            if (generated.size() != documents.size()) {
                return new IndexResult(documents.size(), 0, false);
            }
        } catch (EmbeddingException ignored) {
            return new IndexResult(documents.size(), 0, false);
        }
        for (int i = 0; i < documents.size(); i++) {
            RetrievalDocument document = documents.get(i);
            vectors.upsert(document.namespace(), document.id(), embeddings.model(), generated.get(i));
        }
        return new IndexResult(documents.size(), documents.size(), true);
    }

    public void delete(String namespace, String documentId) {
        db.inTransaction("retrieval.delete", () -> {
            vectors.delete(namespace, documentId);
            lexical.delete(namespace, documentId);
            return null;
        });
    }

    /**
     * Returns lexical results when embeddings are absent, time out, or return malformed data.
     * Successful dual-channel retrieval is fused with RRF k=60 before optional reranking.
     */
    public List<RetrievalHit> retrieve(RetrievalQuery query) {
        List<RetrievalHit> lexicalHits = lexical.search(query);
        if (embeddings == null || query.text().isBlank()) {
            return reranker.rerank(query, lexicalHits, query.limit());
        }

        final float[] queryVector;
        try {
            queryVector = embeddings.embed(query.text());
        } catch (EmbeddingException ignored) {
            return reranker.rerank(query, lexicalHits, query.limit());
        }

        List<RetrievalHit> vectorHits = new ArrayList<>();
        for (VectorRetrievalStore.VectorMatch match : vectors.search(query, queryVector)) {
            Optional<RetrievalDocument> document = lexical.find(query.namespace(), match.documentId());
            document.ifPresent(value -> vectorHits.add(new RetrievalHit(
                    value, match.score(), null, match.score(), Set.of("vector"))));
        }
        List<RetrievalHit> fused = ReciprocalRankFusion.fuse(
                query.candidateLimit(), lexicalHits, vectorHits);
        return reranker.rerank(query, fused, query.limit());
    }
}
