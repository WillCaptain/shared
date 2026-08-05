package org.twelve.shared.retrieval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.twelve.shared.dbops.AtomicDbOps;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parameterized lexical store using PostgreSQL FTS with deterministic JVM scoring fallback. */
public final class LexicalRetrievalStore {
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");

    private final AtomicDbOps db;
    private final RetrievalSchema schema;
    private final ObjectMapper json;
    private final boolean postgres;

    public LexicalRetrievalStore(AtomicDbOps db, RetrievalSchema schema) {
        this(db, schema, new ObjectMapper());
    }

    public LexicalRetrievalStore(AtomicDbOps db, RetrievalSchema schema, ObjectMapper json) {
        this.db = db;
        this.schema = schema;
        this.json = json;
        this.postgres = RetrievalDdl.isPostgres(db);
    }

    public void upsert(RetrievalDocument document) {
        String metadata = writeMetadata(document.metadata());
        Timestamp now = Timestamp.from(Instant.now());
        String table = schema.lexicalQualified();
        int changed = db.update("retrieval.lexical.update",
                "UPDATE " + table + """
                   SET path = ?, kind = ?, title = ?, body = ?, content_hash = ?,
                       metadata_json = ?, updated_at = ?
                 WHERE namespace_key = ? AND document_id = ?
                """,
                document.path(), document.kind(), document.title(), document.body(),
                document.contentHash(), metadata, now, document.namespace(), document.id());
        if (changed == 0) {
            db.update("retrieval.lexical.insert",
                    "INSERT INTO " + table + """
                        (namespace_key, document_id, path, kind, title, body, content_hash,
                         metadata_json, updated_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    document.namespace(), document.id(), document.path(), document.kind(),
                    document.title(), document.body(), document.contentHash(), metadata, now);
        }
        refreshFts(document.namespace(), document.id());
    }

    public void delete(String namespace, String documentId) {
        db.update("retrieval.lexical.delete",
                "DELETE FROM " + schema.lexicalQualified()
                        + " WHERE namespace_key = ? AND document_id = ?",
                namespace, documentId);
    }

    public Optional<RetrievalDocument> find(String namespace, String documentId) {
        List<RetrievalDocument> rows = db.query("retrieval.lexical.find",
                selectColumns() + " FROM " + schema.lexicalQualified()
                        + " WHERE namespace_key = ? AND document_id = ?",
                (rs, row) -> mapDocument(
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)),
                namespace, documentId);
        return rows.stream().findFirst();
    }

    public List<RetrievalHit> search(RetrievalQuery query) {
        if (query.text().isBlank()) return List.of();
        if (postgres) {
            try {
                return searchPostgres(query);
            } catch (RuntimeException ignored) {
                // A missing generated column or restricted role falls through to portable scoring.
            }
        }
        return searchPortable(query);
    }

    private List<RetrievalHit> searchPostgres(RetrievalQuery query) {
        String kindFilter = query.kind().isBlank() ? "" : " AND kind = ?";
        String sql = selectColumns() + """
                , ts_rank_cd(search_tsv, websearch_to_tsquery('simple', ?)) AS score
                  FROM %s
                 WHERE namespace_key = ?%s
                   AND search_tsv @@ websearch_to_tsquery('simple', ?)
                 ORDER BY score DESC, path
                 LIMIT ?
                """.formatted(schema.lexicalQualified(), kindFilter);
        Object[] args = query.kind().isBlank()
                ? new Object[]{query.text(), query.namespace(), query.text(), query.candidateLimit()}
                : new Object[]{query.text(), query.namespace(), query.kind(), query.text(),
                        query.candidateLimit()};
        return db.query("retrieval.lexical.fts", sql,
                (rs, row) -> {
                    RetrievalDocument document = mapDocument(
                            rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8));
                    double score = rs.getDouble(9);
                    return new RetrievalHit(document, score, score, null, Set.of("lexical"));
                }, args);
    }

    private List<RetrievalHit> searchPortable(RetrievalQuery query) {
        String kindFilter = query.kind().isBlank() ? "" : " AND kind = ?";
        Object[] args = query.kind().isBlank()
                ? new Object[]{query.namespace()} : new Object[]{query.namespace(), query.kind()};
        List<RetrievalDocument> documents = db.query("retrieval.lexical.portable_rows",
                selectColumns() + " FROM " + schema.lexicalQualified()
                        + " WHERE namespace_key = ?" + kindFilter,
                (rs, row) -> mapDocument(
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)),
                args);
        Set<String> queryTokens = tokens(query.text());
        if (queryTokens.isEmpty()) return List.of();
        String phrase = query.text().toLowerCase(Locale.ROOT);
        List<RetrievalHit> hits = new ArrayList<>();
        for (RetrievalDocument document : documents) {
            String text = document.embeddingText().toLowerCase(Locale.ROOT);
            Set<String> documentTokens = tokens(text);
            long matches = queryTokens.stream().filter(documentTokens::contains).count();
            if (matches == 0) continue;
            double score = (double) matches / queryTokens.size();
            if (text.contains(phrase)) score += 0.5;
            hits.add(new RetrievalHit(document, score, score, null, Set.of("lexical")));
        }
        hits.sort(Comparator.comparingDouble(RetrievalHit::score).reversed()
                .thenComparing(hit -> hit.document().path()));
        return List.copyOf(hits.subList(0, Math.min(hits.size(), query.candidateLimit())));
    }

    private void refreshFts(String namespace, String documentId) {
        if (!postgres) return;
        try {
            db.update("retrieval.lexical.refresh_tsv",
                    "UPDATE " + schema.lexicalQualified() + """
                       SET search_tsv = to_tsvector('simple',
                           coalesce(title, '') || ' ' || coalesce(path, '') || ' ' || coalesce(body, ''))
                     WHERE namespace_key = ? AND document_id = ?
                    """, namespace, documentId);
        } catch (RuntimeException ignored) {
            // Row remains available through portable token scoring.
        }
    }

    private RetrievalDocument mapDocument(
            String namespace, String id, String path, String kind, String title, String body,
            String contentHash, String metadataJson) {
        Map<String, Object> metadata;
        try {
            metadata = json.readValue(metadataJson, new TypeReference<>() {});
        } catch (JsonProcessingException | IllegalArgumentException ignored) {
            metadata = Map.of();
        }
        return new RetrievalDocument(namespace, id, path, kind, title, body, contentHash, metadata);
    }

    private String writeMetadata(Map<String, Object> metadata) {
        try {
            return json.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("metadata is not JSON serializable", e);
        }
    }

    private static String selectColumns() {
        return "SELECT namespace_key, document_id, path, kind, title, body, content_hash, metadata_json";
    }

    private static Set<String> tokens(String text) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(text == null ? "" : text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() > 1) result.add(token);
        }
        return result;
    }
}
