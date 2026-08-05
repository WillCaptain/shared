package org.twelve.shared.retrieval;

import org.twelve.shared.dbops.AtomicDbOps;

import java.util.List;
import java.util.Locale;

/** Reusable, idempotent DDL for portable tables and optional PostgreSQL accelerators. */
public final class RetrievalDdl {
    private RetrievalDdl() {}

    public static void initialize(AtomicDbOps db, RetrievalSchema schema) {
        db.execute("retrieval.schema.create", "CREATE SCHEMA IF NOT EXISTS " + schema.schema());
        int n = 0;
        for (String statement : coreStatements(schema)) {
            db.execute("retrieval.schema.core_" + (++n), statement);
        }
        if (isPostgres(db)) enablePostgresFeatures(db, schema);
    }

    public static List<String> coreStatements(RetrievalSchema schema) {
        String lexical = schema.lexicalQualified();
        String vector = schema.vectorQualified();
        return List.of(
                """
                CREATE TABLE IF NOT EXISTS %s (
                    namespace_key VARCHAR(200) NOT NULL,
                    document_id VARCHAR(300) NOT NULL,
                    path TEXT NOT NULL,
                    kind VARCHAR(100) NOT NULL,
                    title TEXT NOT NULL,
                    body TEXT NOT NULL,
                    content_hash VARCHAR(200) NOT NULL,
                    metadata_json TEXT NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (namespace_key, document_id)
                )
                """.formatted(lexical),
                "CREATE INDEX IF NOT EXISTS " + schema.lexicalTable() + "_kind_idx ON "
                        + lexical + " (namespace_key, kind)",
                """
                CREATE TABLE IF NOT EXISTS %s (
                    namespace_key VARCHAR(200) NOT NULL,
                    document_id VARCHAR(300) NOT NULL,
                    model VARCHAR(200) NOT NULL,
                    dims INTEGER NOT NULL,
                    embedding_json TEXT NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (namespace_key, document_id)
                )
                """.formatted(vector),
                "CREATE INDEX IF NOT EXISTS " + schema.vectorTable() + "_dims_idx ON "
                        + vector + " (namespace_key, dims)");
    }

    public static void enablePostgresFeatures(AtomicDbOps db, RetrievalSchema schema) {
        try {
            db.execute("retrieval.schema.add_tsv",
                    "ALTER TABLE " + schema.lexicalQualified()
                            + " ADD COLUMN IF NOT EXISTS search_tsv tsvector");
            db.execute("retrieval.schema.add_tsv_idx",
                    "CREATE INDEX IF NOT EXISTS " + schema.lexicalTable() + "_tsv_idx ON "
                            + schema.lexicalQualified() + " USING GIN (search_tsv)");
        } catch (RuntimeException ignored) {
            // Portable token scoring remains available under restricted database roles.
        }
        try {
            db.execute("retrieval.schema.pgvector_extension", "CREATE EXTENSION IF NOT EXISTS vector");
            db.execute("retrieval.schema.add_vector",
                    "ALTER TABLE " + schema.vectorQualified()
                            + " ADD COLUMN IF NOT EXISTS embedding vector");
        } catch (RuntimeException ignored) {
            // embedding_json remains the source for portable cosine search.
        }
    }

    static boolean isPostgres(AtomicDbOps db) {
        try {
            String version = db.queryForObjectNullable(
                    "retrieval.db.version", "SELECT version()", String.class);
            return version != null && version.toLowerCase(Locale.ROOT).contains("postgresql");
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
