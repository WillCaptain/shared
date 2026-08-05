# hybrid-retrieval

Reusable Java 21 retrieval engine for AIPP applications.

It indexes each domain document into two PostgreSQL-backed channels:

1. lexical recall through PostgreSQL full-text search (portable JVM token fallback);
2. semantic recall through OpenAI-compatible embeddings and pgvector (JSON cosine fallback).

The ranked lists are merged with reciprocal-rank fusion (`k = 60`) and may then pass through a
domain-specific `CandidateReranker`.

Applications retain ownership of document construction, tenant namespaces, authorization,
configuration, and result presentation. The engine does not expose an AIPP tool by itself.

## Main API

- `HybridRetrievalEngine`
- `RetrievalDocument`
- `RetrievalQuery`
- `RetrievalHit`
- `EmbeddingProvider`
- `CandidateReranker`
- `RetrievalSchema`

Call `HybridRetrievalEngine.initialize()` once for an application's configured tables, index
domain records with `index`/`indexAll`, and query with `retrieve`.

If the embedding provider is absent or unavailable, indexing and retrieval continue in
lexical-only mode.
