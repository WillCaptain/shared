package org.twelve.shared.retrieval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.twelve.shared.dbops.AtomicDbOps;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Parameterized dense-vector store using pgvector when available and JSON cosine otherwise. */
public final class VectorRetrievalStore {
    private final AtomicDbOps db;
    private final RetrievalSchema schema;
    private final ObjectMapper json;
    private volatile Boolean pgvectorAvailable;

    public record VectorMatch(String documentId, double score) {}

    public VectorRetrievalStore(AtomicDbOps db, RetrievalSchema schema) {
        this(db, schema, new ObjectMapper());
    }

    public VectorRetrievalStore(AtomicDbOps db, RetrievalSchema schema, ObjectMapper json) {
        this.db = db;
        this.schema = schema;
        this.json = json;
    }

    public void upsert(String namespace, String documentId, String model, float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("vector must not be empty");
        }
        String encoded;
        try {
            encoded = json.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("vector is not JSON serializable", e);
        }
        Timestamp now = Timestamp.from(Instant.now());
        String table = schema.vectorQualified();
        int changed = db.update("retrieval.vector.update",
                "UPDATE " + table + """
                   SET model = ?, dims = ?, embedding_json = ?, updated_at = ?
                 WHERE namespace_key = ? AND document_id = ?
                """, model, vector.length, encoded, now, namespace, documentId);
        if (changed == 0) {
            db.update("retrieval.vector.insert",
                    "INSERT INTO " + table + """
                        (namespace_key, document_id, model, dims, embedding_json, updated_at)
                     VALUES (?, ?, ?, ?, ?, ?)
                    """, namespace, documentId, model, vector.length, encoded, now);
        }
        writePgvector(namespace, documentId, vector);
    }

    public void delete(String namespace, String documentId) {
        db.update("retrieval.vector.delete",
                "DELETE FROM " + schema.vectorQualified()
                        + " WHERE namespace_key = ? AND document_id = ?",
                namespace, documentId);
    }

    public List<VectorMatch> search(RetrievalQuery query, float[] queryVector) {
        if (queryVector == null || queryVector.length == 0) return List.of();
        if (hasPgvector()) {
            try {
                return searchPgvector(query, queryVector);
            } catch (RuntimeException ignored) {
                pgvectorAvailable = false;
            }
        }
        return searchJson(query, queryVector);
    }

    private List<VectorMatch> searchPgvector(RetrievalQuery query, float[] vector) {
        String kindFilter = query.kind().isBlank() ? "" : " AND l.kind = ?";
        String sql = """
                SELECT v.document_id, 1 - (v.embedding <=> ?::vector) AS score
                  FROM %s v
                  JOIN %s l
                    ON l.namespace_key = v.namespace_key AND l.document_id = v.document_id
                 WHERE v.namespace_key = ? AND v.dims = ? AND v.embedding IS NOT NULL%s
                 ORDER BY v.embedding <=> ?::vector
                 LIMIT ?
                """.formatted(schema.vectorQualified(), schema.lexicalQualified(), kindFilter);
        String literal = vectorLiteral(vector);
        Object[] args = query.kind().isBlank()
                ? new Object[]{literal, query.namespace(), vector.length, literal, query.candidateLimit()}
                : new Object[]{literal, query.namespace(), vector.length, query.kind(), literal,
                        query.candidateLimit()};
        return db.query("retrieval.vector.pgvector_search", sql,
                (rs, row) -> new VectorMatch(rs.getString(1), rs.getDouble(2)), args);
    }

    private List<VectorMatch> searchJson(RetrievalQuery query, float[] queryVector) {
        String kindFilter = query.kind().isBlank() ? "" : " AND l.kind = ?";
        String sql = """
                SELECT v.document_id, v.embedding_json
                  FROM %s v
                  JOIN %s l
                    ON l.namespace_key = v.namespace_key AND l.document_id = v.document_id
                 WHERE v.namespace_key = ? AND v.dims = ?%s
                """.formatted(schema.vectorQualified(), schema.lexicalQualified(), kindFilter);
        Object[] args = query.kind().isBlank()
                ? new Object[]{query.namespace(), queryVector.length}
                : new Object[]{query.namespace(), queryVector.length, query.kind()};
        List<StoredVector> stored = db.query("retrieval.vector.json_rows", sql,
                (rs, row) -> {
                    try {
                        return new StoredVector(rs.getString(1),
                                json.readValue(rs.getString(2), float[].class));
                    } catch (JsonProcessingException e) {
                        return new StoredVector(rs.getString(1), new float[0]);
                    }
                }, args);
        List<VectorMatch> matches = new ArrayList<>();
        for (StoredVector item : stored) {
            if (item.vector().length != queryVector.length) continue;
            double score = cosine(queryVector, item.vector());
            if (Double.isFinite(score)) matches.add(new VectorMatch(item.documentId(), score));
        }
        matches.sort(Comparator.comparingDouble(VectorMatch::score).reversed()
                .thenComparing(VectorMatch::documentId));
        return List.copyOf(matches.subList(0, Math.min(matches.size(), query.candidateLimit())));
    }

    private void writePgvector(String namespace, String documentId, float[] vector) {
        if (!hasPgvector()) return;
        try {
            db.update("retrieval.vector.pgvector_update",
                    "UPDATE " + schema.vectorQualified()
                            + " SET embedding = ?::vector WHERE namespace_key = ? AND document_id = ?",
                    vectorLiteral(vector), namespace, documentId);
        } catch (RuntimeException ignored) {
            pgvectorAvailable = false;
        }
    }

    private boolean hasPgvector() {
        Boolean known = pgvectorAvailable;
        if (known != null) return known;
        if (!RetrievalDdl.isPostgres(db)) {
            pgvectorAvailable = false;
            return false;
        }
        try {
            List<Integer> rows = db.query("retrieval.vector.has_pgvector",
                    """
                    SELECT 1 FROM information_schema.columns
                     WHERE table_schema = ? AND table_name = ? AND column_name = 'embedding'
                     LIMIT 1
                    """, (rs, row) -> 1, schema.schema(), schema.vectorTable());
            pgvectorAvailable = !rows.isEmpty();
        } catch (RuntimeException ignored) {
            pgvectorAvailable = false;
        }
        return pgvectorAvailable;
    }

    public static double cosine(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || left.length != right.length) return 0;
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) return 0;
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private static String vectorLiteral(float[] vector) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) result.append(',');
            result.append(Float.toString(vector[i]));
        }
        return result.append(']').toString();
    }

    private record StoredVector(String documentId, float[] vector) {}
}
