package org.twelve.shared.retrieval;

import org.junit.jupiter.api.Test;
import org.twelve.shared.dbops.AtomicDbOps;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridRetrievalEngineTest {
    private static final RetrievalSchema SCHEMA =
            new RetrievalSchema("search_test", "lexical_docs", "dense_vectors");

    @Test
    void indexesAndRetrievesWithH2JsonCosineAndPortableLexicalSearch() {
        AtomicDbOps db = TestDatabase.create();
        EmbeddingProvider provider = new KeywordEmbeddingProvider(false);
        HybridRetrievalEngine engine = new HybridRetrievalEngine(db, SCHEMA, provider);
        engine.initialize();

        HybridRetrievalEngine.IndexResult indexed = engine.indexAll(List.of(
                document("apple", "Apple orchard", "Growing apples and pears"),
                document("banana", "Banana guide", "Tropical yellow fruit")));

        assertEquals(2, indexed.lexicalIndexed());
        assertEquals(2, indexed.vectorIndexed());
        List<RetrievalHit> hits =
                engine.retrieve(new RetrievalQuery("tenant-a", "apple orchard", "", 2, 4));

        assertFalse(hits.isEmpty());
        assertEquals("apple", hits.getFirst().document().id());
        assertEquals("owner-a", hits.getFirst().document().metadata().get("owner"));
        assertTrue(hits.getFirst().sources().contains("lexical"));
        assertTrue(hits.getFirst().sources().contains("vector"));
    }

    @Test
    void embeddingFailurePreservesLexicalOnlyIndexAndQuery() {
        AtomicDbOps db = TestDatabase.create();
        HybridRetrievalEngine engine =
                new HybridRetrievalEngine(db, SCHEMA, new KeywordEmbeddingProvider(true));
        engine.initialize();

        HybridRetrievalEngine.IndexResult indexed =
                engine.index(document("orchard", "Orchard notes", "Pruning schedule"));
        List<RetrievalHit> hits =
                engine.retrieve(new RetrievalQuery("tenant-a", "pruning orchard", 5));

        assertEquals(1, indexed.lexicalIndexed());
        assertEquals(0, indexed.vectorIndexed());
        assertFalse(indexed.embeddingsAvailable());
        assertEquals(List.of("orchard"), hits.stream().map(h -> h.document().id()).toList());
        assertEquals(java.util.Set.of("lexical"), hits.getFirst().sources());
    }

    @Test
    void schemaAndTableNamesAreActuallyParameterized() {
        AtomicDbOps db = TestDatabase.create();
        HybridRetrievalEngine engine = new HybridRetrievalEngine(db,
                new RetrievalSchema("custom_schema", "custom_lexical", "custom_vectors"), null);
        engine.initialize();
        engine.index(document("one", "Portable", "custom table"));

        Integer count = db.queryForObjectNullable("test.count",
                "SELECT COUNT(*) FROM custom_schema.custom_lexical", Integer.class);
        assertEquals(1, count);
    }

    private static RetrievalDocument document(String id, String title, String body) {
        return new RetrievalDocument(
                "tenant-a", id, "/docs/" + id, "document", title, body, "hash-" + id,
                Map.of("owner", "owner-a"));
    }

    private static final class KeywordEmbeddingProvider implements EmbeddingProvider {
        private final boolean fail;

        private KeywordEmbeddingProvider(boolean fail) {
            this.fail = fail;
        }

        @Override
        public String model() {
            return "keyword-v1";
        }

        @Override
        public List<float[]> embed(List<String> inputs) throws EmbeddingException {
            if (fail) throw new EmbeddingException("endpoint unavailable");
            return inputs.stream().map(input -> {
                String value = input.toLowerCase();
                return new float[]{
                        value.contains("apple") || value.contains("orchard") ? 1 : 0,
                        value.contains("banana") || value.contains("tropical") ? 1 : 0
                };
            }).toList();
        }
    }
}
